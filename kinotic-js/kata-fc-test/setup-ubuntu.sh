#!/usr/bin/env bash
# Installs the Kata Containers + Firecracker + containerd + nerdctl stack on a fresh Ubuntu
# 22.04 host with nested virtualization, then verifies each piece is present.
#
# Firecracker differs from Cloud Hypervisor in one way that shapes this whole script: it has
# no filesystem sharing at all — configuration-fc.toml carries no shared_fs setting, where
# configuration-clh.toml sets shared_fs = "virtio-fs". A workload's rootfs must therefore
# arrive as a block device, which means containerd needs the devmapper snapshotter over a
# thin-pool rather than the default overlayfs, and bind-mounted volumes are not available.
#
# Release versions are resolved from GitHub at run time rather than pinned here, and every
# resolved version is printed, so a run always reports exactly what it tested. Any failure
# aborts with SETUP FAILED and the command output that caused it — a half-installed stack
# would otherwise fall back to runc and make every capability probe pass for the wrong reason.
set -euo pipefail

step()  { echo; echo "=== $*"; }
fail()  { echo "SETUP FAILED: $*" >&2; exit 1; }

POOL_NAME=kata-fc-pool
SNAPSHOTTER=devmapper
DEVMAPPER_ROOT=/var/lib/containerd/devmapper
DATA_SIZE=40G
META_SIZE=4G

[ "$(id -u)" -eq 0 ] || fail "run as root (sudo $0)"
[ -e /dev/kvm ] || fail "/dev/kvm is missing — this host has no nested virtualization, nothing below will work"

step "Base packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl jq tar xz-utils zstd iptables ca-certificates dmsetup thin-provisioning-tools >/dev/null

# Kata's agent talks to the shim over vsock; without this module every box start hangs or fails
modprobe vhost_vsock 2>/dev/null || fail "could not load vhost_vsock, which kata needs to reach the guest agent"

step "Resolving latest releases"
# The kata release has published kata-static as .tar.xz and as .tar.zst at different times, and
# a constructed name 404s on the format it did not use, so pick the asset out of the release JSON
KATA_JSON="$(curl -fsSL https://api.github.com/repos/kata-containers/kata-containers/releases/latest)"
KATA_VERSION="$(printf '%s' "$KATA_JSON" | jq -r .tag_name)"
KATA_URL="$(printf '%s' "$KATA_JSON" | jq -r '.assets[] | select(.name | startswith("kata-static-") and contains("-amd64.tar")) | .browser_download_url' | head -n 1)"
[ -n "$KATA_VERSION" ] && [ "$KATA_VERSION" != "null" ] || fail "could not resolve the kata-containers release"
[ -n "$KATA_URL" ] || fail "release $KATA_VERSION has no kata-static amd64 asset; it published: $(printf '%s' "$KATA_JSON" | jq -r '.assets[].name' | tr '\n' ' ')"

NERDCTL_JSON="$(curl -fsSL https://api.github.com/repos/containerd/nerdctl/releases/latest)"
NERDCTL_VERSION="$(printf '%s' "$NERDCTL_JSON" | jq -r .tag_name)"
NERDCTL_URL="$(printf '%s' "$NERDCTL_JSON" | jq -r '.assets[] | select(.name | startswith("nerdctl-full-") and endswith("-linux-amd64.tar.gz")) | .browser_download_url' | head -n 1)"
[ -n "$NERDCTL_URL" ] || fail "release $NERDCTL_VERSION has no nerdctl-full amd64 asset"

KATA_ASSET="${KATA_URL##*/}"
NERDCTL_ASSET="${NERDCTL_URL##*/}"
echo "kata-containers : $KATA_VERSION  ($KATA_ASSET)"
echo "nerdctl         : $NERDCTL_VERSION  ($NERDCTL_ASSET)"

step "Installing Kata Containers (static bundle: shim, guest kernel, guest image, firecracker)"
curl -fsSL -o "/tmp/${KATA_ASSET}" "$KATA_URL" || fail "downloading ${KATA_ASSET}"
case "$KATA_ASSET" in
    *.tar.zst) tar --use-compress-program=unzstd -xf "/tmp/${KATA_ASSET}" -C / || fail "extracting ${KATA_ASSET}" ;;
    *.tar.xz)  tar -xJf "/tmp/${KATA_ASSET}" -C / || fail "extracting ${KATA_ASSET}" ;;
    *)         fail "unhandled archive format for ${KATA_ASSET}" ;;
esac
[ -x /opt/kata/bin/containerd-shim-kata-v2 ] || fail "the bundle did not provide /opt/kata/bin/containerd-shim-kata-v2"
[ -f /opt/kata/bin/firecracker ] || fail "the bundle did not ship a firecracker binary"
# The bundle ships firecracker 0744, so a non-root caller cannot exec it
chmod 0755 /opt/kata/bin/firecracker /opt/kata/bin/jailer

ln -sf /opt/kata/bin/containerd-shim-kata-v2 /usr/local/bin/containerd-shim-kata-fc-v2
ln -sf /opt/kata/bin/containerd-shim-kata-v2 /usr/local/bin/containerd-shim-kata-v2
FC_CONF=/opt/kata/share/defaults/kata-containers/configuration-fc.toml
[ -f "$FC_CONF" ] || fail "no Firecracker configuration at $FC_CONF — this bundle may not ship fc support"

# The shim does not derive its config from the name it was invoked under: kata-runtime reads
# /etc/kata-containers/configuration.toml first and the bundle default second, and the bundle
# ships that default as a symlink to configuration-qemu.toml. Without this override the stack
# runs QEMU while every version string still reports firecracker.
mkdir -p /etc/kata-containers
ln -sf "$FC_CONF" /etc/kata-containers/configuration.toml

step "Installing containerd, CNI and nerdctl (nerdctl-full bundle)"
curl -fsSL -o "/tmp/${NERDCTL_ASSET}" "$NERDCTL_URL" || fail "downloading ${NERDCTL_ASSET}"
tar -xzf "/tmp/${NERDCTL_ASSET}" -C /usr/local || fail "extracting ${NERDCTL_ASSET}"

step "Creating the devmapper thin-pool (${DATA_SIZE} data, ${META_SIZE} metadata)"
# Firecracker has no virtiofs, so a workload's rootfs must be a block device rather than an
# overlayfs directory. The pool is backed by sparse files on loop devices, which do not
# survive a reboot — re-run this script after one.
mkdir -p "$DEVMAPPER_ROOT"
if dmsetup info "$POOL_NAME" >/dev/null 2>&1; then
    echo "thin-pool ${POOL_NAME} already present, reusing it"
else
    [ -f "$DEVMAPPER_ROOT/data" ] || truncate -s "$DATA_SIZE" "$DEVMAPPER_ROOT/data"
    [ -f "$DEVMAPPER_ROOT/meta" ] || truncate -s "$META_SIZE" "$DEVMAPPER_ROOT/meta"
    DATA_DEV="$(losetup --find --show "$DEVMAPPER_ROOT/data")" || fail "could not attach a loop device to the pool data file"
    META_DEV="$(losetup --find --show "$DEVMAPPER_ROOT/meta")" || fail "could not attach a loop device to the pool metadata file"
    SECTORS=$(( $(blockdev --getsize64 -q "$DATA_DEV") / 512 ))
    # 128 sectors (64KB) data blocks, low-water mark 32768 — the values containerd documents
    dmsetup create "$POOL_NAME" --table "0 ${SECTORS} thin-pool ${META_DEV} ${DATA_DEV} 128 32768" \
        || fail "dmsetup could not create the thin-pool"
    echo "created thin-pool ${POOL_NAME} on ${DATA_DEV} / ${META_DEV}"
fi

step "Configuring containerd for the devmapper snapshotter"
mkdir -p /etc/containerd
containerd config default > /etc/containerd/config.toml 2>/dev/null || fail "containerd config default failed"
# The generated config already declares an empty devmapper table, so fill that one in rather
# than appending a second — a duplicate table makes containerd refuse to parse the file
grep -q "io.containerd.snapshotter.v1.devmapper" /etc/containerd/config.toml \
    || fail "the generated containerd config has no devmapper snapshotter table to configure"
python3 - "$POOL_NAME" "$DEVMAPPER_ROOT" <<'PY' || fail "could not set the devmapper snapshotter options"
import re, sys
pool, root = sys.argv[1], sys.argv[2]
path = "/etc/containerd/config.toml"
text = open(path).read()
settings = {
    "root_path": f"'{root}'",
    "pool_name": f"'{pool}'",
    "base_image_size": "'10GB'",
    "discard_blocks": "true",
}
def fill(match):
    body = match.group(2)
    for key, value in settings.items():
        body = re.sub(rf"^(\s*){key} = .*$", rf"\g<1>{key} = {value}", body, flags=re.M)
    return match.group(1) + body
# Rewrite only the devmapper table: from its header up to the next table header
pattern = re.compile(r"(\[plugins\.'io\.containerd\.snapshotter\.v1\.devmapper'\]\n)((?:(?!\n\s*\[).)*)", re.S)
text, count = pattern.subn(fill, text, count=1)
if count != 1:
    raise SystemExit("devmapper table not matched")
open(path, "w").write(text)
PY

# containerd 2.x will only unpack an image for a (platform, snapshotter) pair listed here, and
# the generated config lists none — overlayfs works from a builtin default, so without this
# entry every pull into devmapper fails with "no unpack platforms defined"
cat >> /etc/containerd/config.toml <<EOF

[[plugins.'io.containerd.transfer.v1.local'.unpack_config]]
  platform = 'linux/amd64'
  snapshotter = '${SNAPSHOTTER}'
EOF

step "Starting containerd"
systemctl daemon-reload
systemctl enable --now containerd >/dev/null 2>&1 || fail "containerd did not start; systemctl status containerd"
systemctl restart containerd
sleep 3
systemctl is-active --quiet containerd || fail "containerd is not active after start"
# Captured first rather than piped into grep -q: under pipefail an early-exiting grep closes
# the pipe, ctr dies on SIGPIPE, and the pipeline reports failure even on a match
PLUGINS="$(ctr plugins ls 2>/dev/null || true)"
case "$PLUGINS" in
    *devmapper*ok*) : ;;
    *) fail "the devmapper snapshotter did not load: $(printf '%s\n' "$PLUGINS" | grep devmapper | head -1)" ;;
esac

step "Verifying the pieces"
/usr/local/bin/nerdctl --version || fail "nerdctl is not runnable"
/opt/kata/bin/kata-runtime --version | head -n 3 || fail "kata-runtime is not runnable"
FC_BIN="$(grep -E '^\s*path\s*=' "$FC_CONF" | head -n 1 | sed -E 's/.*"(.*)".*/\1/')"
echo "firecracker path from configuration-fc.toml: ${FC_BIN:-(not found)}"
[ -x "${FC_BIN:-/nonexistent}" ] || fail "the firecracker binary named by $FC_CONF is missing or not executable"
"$FC_BIN" --version | head -n 1 || fail "firecracker is not runnable"

step "Pulling the probe images"
/usr/local/bin/nerdctl pull -q --snapshotter "$SNAPSHOTTER" alpine:latest >/dev/null || fail "could not pull alpine:latest into the devmapper snapshotter"
/usr/local/bin/nerdctl pull -q --snapshotter "$SNAPSHOTTER" nginx:alpine >/dev/null || fail "could not pull nginx:alpine into the devmapper snapshotter"

step "Smoke test: one container on the Kata Firecracker runtime"
GUEST_KERNEL="$(/usr/local/bin/nerdctl run --rm --snapshotter "$SNAPSHOTTER" --runtime io.containerd.kata-fc.v2 alpine:latest uname -r)" \
    || fail "a container would not start on io.containerd.kata-fc.v2 — check: journalctl -u containerd -n 50"
echo "host kernel  : $(uname -r)"
echo "guest kernel : ${GUEST_KERNEL}"
[ "$GUEST_KERNEL" != "$(uname -r)" ] \
    || fail "the guest reported the host's kernel, so this ran under runc and not in a VM at all"

# A differing guest kernel only proves a VM booted, not which hypervisor booted it, so assert
# the VMM process directly. This compares each process's actual executable via /proc/PID/exe:
# a pgrep -f pattern would match this script's own command line, and comm is truncated to 15
# characters so it never equals a longer binary name either.
# Compares the basename rather than the full path because kata launches firecracker through
# the jailer, which chroots it — /proc/PID/exe then resolves to /firecracker, not the bundle
# path. Still reads the executable the kernel recorded, so neither a command line containing
# the name nor comm's 15-character truncation can produce a wrong answer.
count_vmm() {
    local pid exe found=0
    for pid in /proc/[0-9]*; do
        exe="$(readlink -f "$pid/exe" 2>/dev/null)" || continue
        [ "${exe##*/}" = "$1" ] && found=$((found + 1))
    done
    echo "$found"
}
/usr/local/bin/nerdctl run -d --name kata-fc-vmmcheck --snapshotter "$SNAPSHOTTER" \
    --runtime io.containerd.kata-fc.v2 alpine:latest sleep 30 >/dev/null 2>&1 \
    || fail "could not start the VMM verification container"
sleep 5
RUNNING_FC="$(count_vmm firecracker)"
RUNNING_QEMU="$(count_vmm qemu-system-x86_64)"
RUNNING_CLH="$(count_vmm cloud-hypervisor)"
/usr/local/bin/nerdctl rm -f kata-fc-vmmcheck >/dev/null 2>&1
[ "$RUNNING_FC" -gt 0 ] \
    || fail "a kata-fc container ran with no firecracker process (qemu: ${RUNNING_QEMU}, cloud-hypervisor: ${RUNNING_CLH}) — the shim resolved a different hypervisor config; check kata-runtime env"
echo "hypervisor   : firecracker (${RUNNING_FC} process(es) verified, qemu: ${RUNNING_QEMU}, clh: ${RUNNING_CLH})"

echo
echo "SETUP OK — kata ${KATA_VERSION}, nerdctl ${NERDCTL_VERSION}, guest kernel ${GUEST_KERNEL}, snapshotter ${SNAPSHOTTER}"
