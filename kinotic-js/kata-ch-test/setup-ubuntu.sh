#!/usr/bin/env bash
# Installs the Kata Containers + Cloud Hypervisor + containerd + nerdctl stack on a fresh
# Ubuntu 22.04 host with nested virtualization, then verifies each piece is present.
#
# Release versions are resolved from GitHub at run time rather than pinned here, and every
# resolved version is printed, so a run always reports exactly what it tested. Any failure
# aborts with SETUP FAILED and the command output that caused it — a half-installed stack
# would otherwise fall back to runc and make every capability probe pass for the wrong
# reason.
set -euo pipefail

step()  { echo; echo "=== $*"; }
fail()  { echo "SETUP FAILED: $*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || fail "run as root (sudo $0)"
[ -e /dev/kvm ] || fail "/dev/kvm is missing — this host has no nested virtualization, nothing below will work"

step "Base packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl jq tar xz-utils zstd iptables ca-certificates >/dev/null

step "Resolving latest releases"
# Asset URLs are taken from the release rather than built from the version. Kata has shipped
# kata-static as .tar.xz and as .tar.zst at different times, and a constructed name 404s on
# whichever release changes it. The prefix match excludes kata-tools-static, which is a
# separate asset in the same release.
KATA_JSON="$(curl -fsSL https://api.github.com/repos/kata-containers/kata-containers/releases/latest)"
KATA_VERSION="$(printf '%s' "$KATA_JSON" | jq -r .tag_name)"
KATA_URL="$(printf '%s' "$KATA_JSON" | jq -r '.assets[] | select(.name | startswith("kata-static-") and contains("-amd64.tar")) | .browser_download_url' | head -n 1)"
[ -n "$KATA_VERSION" ] && [ "$KATA_VERSION" != "null" ] || fail "could not resolve the kata-containers release"
[ -n "$KATA_URL" ] || fail "release $KATA_VERSION has no kata-static amd64 asset; it published: $(printf '%s' "$KATA_JSON" | jq -r '.assets[].name' | tr '\n' ' ')"

NERDCTL_JSON="$(curl -fsSL https://api.github.com/repos/containerd/nerdctl/releases/latest)"
NERDCTL_VERSION="$(printf '%s' "$NERDCTL_JSON" | jq -r .tag_name)"
NERDCTL_URL="$(printf '%s' "$NERDCTL_JSON" | jq -r '.assets[] | select(.name | startswith("nerdctl-full-") and endswith("-linux-amd64.tar.gz")) | .browser_download_url' | head -n 1)"
[ -n "$NERDCTL_VERSION" ] && [ "$NERDCTL_VERSION" != "null" ] || fail "could not resolve the nerdctl release"
[ -n "$NERDCTL_URL" ] || fail "release $NERDCTL_VERSION has no nerdctl-full amd64 asset; it published: $(printf '%s' "$NERDCTL_JSON" | jq -r '.assets[].name' | tr '\n' ' ')"

KATA_ASSET="${KATA_URL##*/}"
NERDCTL_ASSET="${NERDCTL_URL##*/}"
echo "kata-containers : $KATA_VERSION  ($KATA_ASSET)"
echo "nerdctl         : $NERDCTL_VERSION  ($NERDCTL_ASSET)"

step "Installing Kata Containers (static bundle: shim, guest kernel, guest image, cloud-hypervisor)"
curl -fsSL -o "/tmp/${KATA_ASSET}" "$KATA_URL" || fail "downloading ${KATA_ASSET}"
case "$KATA_ASSET" in
    *.tar.zst) tar --use-compress-program=unzstd -xf "/tmp/${KATA_ASSET}" -C / || fail "extracting ${KATA_ASSET}" ;;
    *.tar.xz)  tar -xJf "/tmp/${KATA_ASSET}" -C / || fail "extracting ${KATA_ASSET}" ;;
    *)         fail "unhandled archive format for ${KATA_ASSET}" ;;
esac
[ -x /opt/kata/bin/containerd-shim-kata-v2 ] || fail "the bundle did not provide /opt/kata/bin/containerd-shim-kata-v2"

ln -sf /opt/kata/bin/containerd-shim-kata-v2 /usr/local/bin/containerd-shim-kata-clh-v2
ln -sf /opt/kata/bin/containerd-shim-kata-v2 /usr/local/bin/containerd-shim-kata-v2
CLH_CONF=/opt/kata/share/defaults/kata-containers/configuration-clh.toml
[ -f "$CLH_CONF" ] || fail "no Cloud Hypervisor configuration at $CLH_CONF — this bundle may not ship clh support"

# The shim does not derive its config from the name it was invoked under: kata-runtime reads
# /etc/kata-containers/configuration.toml first and the bundle default second, and the bundle
# ships that default as a symlink to configuration-qemu.toml. Without this override the stack
# runs QEMU while every version string still reports cloud-hypervisor.
mkdir -p /etc/kata-containers
ln -sf "$CLH_CONF" /etc/kata-containers/configuration.toml

step "Installing containerd, CNI and nerdctl (nerdctl-full bundle)"
curl -fsSL -o "/tmp/${NERDCTL_ASSET}" "$NERDCTL_URL" || fail "downloading ${NERDCTL_ASSET}"
tar -xzf "/tmp/${NERDCTL_ASSET}" -C /usr/local || fail "extracting ${NERDCTL_ASSET}"

step "Starting containerd"
systemctl daemon-reload
systemctl enable --now containerd >/dev/null 2>&1 || fail "containerd did not start; systemctl status containerd"
systemctl is-active --quiet containerd || fail "containerd is not active after start"

step "Verifying the pieces"
/usr/local/bin/nerdctl --version || fail "nerdctl is not runnable"
/opt/kata/bin/kata-runtime --version | head -n 3 || fail "kata-runtime is not runnable"
CLH_BIN="$(grep -E '^\s*path\s*=' "$CLH_CONF" | head -n 1 | sed -E 's/.*"(.*)".*/\1/')"
echo "cloud-hypervisor path from configuration-clh.toml: ${CLH_BIN:-(not found)}"
[ -x "${CLH_BIN:-/nonexistent}" ] || fail "the cloud-hypervisor binary named by $CLH_CONF is missing or not executable"
"$CLH_BIN" --version || fail "cloud-hypervisor is not runnable"

step "Pulling the probe images"
/usr/local/bin/nerdctl pull -q alpine:latest >/dev/null || fail "could not pull alpine:latest"
/usr/local/bin/nerdctl pull -q nginx:alpine >/dev/null || fail "could not pull nginx:alpine"

step "Smoke test: one container on the Kata Cloud Hypervisor runtime"
GUEST_KERNEL="$(/usr/local/bin/nerdctl run --rm --runtime io.containerd.kata-clh.v2 alpine:latest uname -r)" \
    || fail "a container would not start on io.containerd.kata-clh.v2 — check: journalctl -u containerd -n 50"
echo "host kernel  : $(uname -r)"
echo "guest kernel : ${GUEST_KERNEL}"
[ "$GUEST_KERNEL" != "$(uname -r)" ] \
    || fail "the guest reported the host's kernel, so this ran under runc and not in a VM at all"

# A differing guest kernel only proves a VM booted, not which hypervisor booted it, so assert
# the VMM process directly. This compares each process's actual executable via /proc/PID/exe:
# a pgrep -f pattern would match this script's own command line, and comm is truncated to 15
# characters so it never equals "cloud-hypervisor" either.
count_vmm() {
    local pid exe found=0
    for pid in /proc/[0-9]*; do
        exe="$(readlink -f "$pid/exe" 2>/dev/null)" || continue
        [ "$exe" = "$1" ] && found=$((found + 1))
    done
    echo "$found"
}
/usr/local/bin/nerdctl run -d --name kata-clh-vmmcheck --runtime io.containerd.kata-clh.v2 \
    alpine:latest sleep 30 >/dev/null 2>&1 || fail "could not start the VMM verification container"
sleep 5
RUNNING_CLH="$(count_vmm /opt/kata/bin/cloud-hypervisor)"
RUNNING_QEMU="$(count_vmm /opt/kata/bin/qemu-system-x86_64)"
/usr/local/bin/nerdctl rm -f kata-clh-vmmcheck >/dev/null 2>&1
[ "$RUNNING_CLH" -gt 0 ] \
    || fail "a kata-clh container ran with no cloud-hypervisor process (qemu-system processes seen: ${RUNNING_QEMU}) — the shim resolved a different hypervisor config; check kata-runtime env"
echo "hypervisor   : cloud-hypervisor (${RUNNING_CLH} process(es) verified, qemu: ${RUNNING_QEMU})"

echo
echo "SETUP OK — kata ${KATA_VERSION}, nerdctl ${NERDCTL_VERSION}, guest kernel ${GUEST_KERNEL}"
