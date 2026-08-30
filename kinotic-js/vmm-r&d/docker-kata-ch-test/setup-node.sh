#!/usr/bin/env bash
# Provisions a node to run Kinotic workloads as Kata microVMs on Cloud Hypervisor, driven
# through the Docker Engine API. Idempotent: safe to re-run on a node already set up.
#
# Docker rather than containerd's CRI for two reasons neither is obvious from the outside:
# CRI has no per-container rootfs size (containerd applies base_image_size per snapshotter,
# not per container), while Docker's --storage-opt size does, and Docker has a maintained
# Node client where CRI has none. Docker still runs the workload through containerd and the
# Kata shim, so the isolation is identical; only the control plane differs.
#
# Everything here has to survive a reboot, because a node that comes back without its XFS
# data root or its firewall rules keeps accepting workloads while silently enforcing nothing.
# verify-node.sh asserts every invariant this script establishes and is run at the end.
set -euo pipefail
step() { echo; echo "=== $*"; }
fail() { echo "SETUP FAILED: $*" >&2; exit 1; }

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DATA=/var/lib/docker
DOCKER_FS_IMAGE=/var/lib/docker-xfs.img
DOCKER_FS_SIZE="${DOCKER_FS_SIZE:-40G}"

[ "$(id -u)" -eq 0 ] || fail "run as root (sudo $0)"
[ -e /dev/kvm ] || fail "/dev/kvm is missing — this host has no nested virtualization"

step "Base packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl jq tar zstd xfsprogs ca-certificates iptables >/dev/null

step "Docker Engine"
# Docker brings its own containerd. Installing nerdctl-full as well would put a second
# containerd on the box and the two fight over /run/containerd/containerd.sock.
if ! command -v dockerd >/dev/null 2>&1; then
    curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
    sh /tmp/get-docker.sh >/dev/null 2>&1 || true
fi
command -v dockerd >/dev/null 2>&1 || fail "docker did not install"
getent group docker >/dev/null || groupadd docker
dpkg --configure -a >/dev/null 2>&1 || true
docker --version | sed 's/^/  /'

step "Kata Containers (static bundle: shim, guest kernel, guest image, cloud-hypervisor)"
# Kata names its assets with Go's GOARCH, which is not what uname reports
case "$(uname -m)" in
    x86_64)  KATA_ARCH=amd64 ;;
    aarch64) KATA_ARCH=arm64 ;;
    *) fail "unsupported architecture $(uname -m)" ;;
esac
KATA_JSON="$(curl -fsSL https://api.github.com/repos/kata-containers/kata-containers/releases/latest)"
KATA_VERSION="$(printf '%s' "$KATA_JSON" | jq -r .tag_name)"
# The release has shipped kata-static as .tar.xz and as .tar.zst at different times, so the
# asset is picked out of the release JSON rather than constructed
KATA_URL="$(printf '%s' "$KATA_JSON" | jq -r --arg arch "-$KATA_ARCH.tar" '.assets[] | select(.name | startswith("kata-static-") and contains($arch)) | .browser_download_url' | head -n 1)"
[ -n "$KATA_URL" ] || fail "release $KATA_VERSION has no kata-static $KATA_ARCH asset"
ASSET="${KATA_URL##*/}"
echo "  kata-containers : $KATA_VERSION ($ASSET)"
curl -fsSL -o "/tmp/$ASSET" "$KATA_URL"
# Removed rather than unpacked over: tar does not delete what a previous release left, and the
# releases do not ship the same set of files. A node provisioned before 4.1.0 keeps that
# release's Go shim and its configuration-clh.toml, both of which the selection below would
# still find and prefer — so re-running this to pick up a fix would leave the node running the
# very build it was upgraded away from.
rm -rf /opt/kata
case "$ASSET" in
    *.tar.zst) tar --use-compress-program=unzstd -xf "/tmp/$ASSET" -C / ;;
    *.tar.xz)  tar -xJf "/tmp/$ASSET" -C / ;;
    *) fail "unhandled archive format for $ASSET" ;;
esac
# The bundle ships cloud-hypervisor/firecracker 0744, unusable by a non-root caller
chmod 0755 /opt/kata/bin/cloud-hypervisor /opt/kata/bin/firecracker 2>/dev/null || true

# Kata ships two runtimes and they do not share a config tree. The Go runtime reads
# /etc/kata-containers/configuration.toml; runtime-rs reads
# /etc/kata-containers/runtime-rs/configuration.toml and its own configuration-*-runtime-rs.toml
# files. Up to 4.0.0 the bundle carried both; 4.1.0 dropped the Go runtime on every
# architecture. The Go runtime stays first so a release that still ships it is provisioned the
# way it always was, and runtime-rs is the fallback rather than a switch.
if [ -x /opt/kata/bin/containerd-shim-kata-v2 ]; then
    KATA_SHIM=/opt/kata/bin/containerd-shim-kata-v2
    KATA_CONF_DIR=/etc/kata-containers
    CLH_CONF=/opt/kata/share/defaults/kata-containers/configuration-clh.toml
elif [ -x /opt/kata/runtime-rs/bin/containerd-shim-kata-v2 ]; then
    KATA_SHIM=/opt/kata/runtime-rs/bin/containerd-shim-kata-v2
    KATA_CONF_DIR=/etc/kata-containers/runtime-rs
    CLH_CONF=/opt/kata/share/defaults/kata-containers/runtime-rs/configuration-clh-runtime-rs.toml
else
    fail "the bundle provided no kata shim"
fi
echo "  shim            : $KATA_SHIM"

# runtime-rs generates a hypervisor's config only when the arch makefile names its binary, and
# aarch64-options.mk never sets CLHCMD — so the arm64 bundle ships cloud-hypervisor and a shim
# with CH support compiled in, but no clh config to select it. Render one from the template for
# the exact release installed above. Delete this once upstream sets CLHCMD for aarch64.
if [ ! -f "$CLH_CONF" ]; then
    case "$CLH_CONF" in
        *runtime-rs*) ;;
        *) fail "no Cloud Hypervisor configuration in this bundle" ;;
    esac
    echo "  clh config      : absent from the bundle, rendering it for $KATA_VERSION"
    TEMPLATE_URL="https://raw.githubusercontent.com/kata-containers/kata-containers/$KATA_VERSION/src/runtime-rs/config/configuration-clh-runtime-rs.toml.in"
    curl -fsSL -o /tmp/configuration-clh-runtime-rs.toml.in "$TEMPLATE_URL" \
        || fail "could not fetch the clh config template for $KATA_VERSION"
    # Values are upstream's own, from src/runtime-rs/Makefile and arch/aarch64-options.mk at
    # this tag; the sed runs on a single stream so an unsubstituted @VAR@ is caught below
    sed -e 's|@CLHPATH@|/opt/kata/bin/cloud-hypervisor|g' \
        -e 's|@CLHVALIDHYPERVISORPATHS@|["/opt/kata/bin/cloud-hypervisor"]|g' \
        -e 's|@KERNELPATH_CLH@|/opt/kata/share/kata-containers/vmlinux.container|g' \
        -e 's|@IMAGEPATH@|/opt/kata/share/kata-containers/kata-containers.img|g' \
        -e 's|@FIRMWAREPATH@||g' \
        -e 's|@DEFROOTFSTYPE@|"ext4"|g' \
        -e 's|@VMROOTFSDRIVER_CLH@|virtio-blk-pci|g' \
        -e 's|@DEFENABLEANNOTATIONS@|["enable_iommu", "kernel_params", "kernel_verity_params", "default_vcpus", "default_memory"]|g' \
        -e 's|@KERNELPARAMS@|cgroup_no_v1=all systemd.unified_cgroup_hierarchy=1|g' \
        -e 's|@DEFVCPUS@|1|g' \
        -e 's|@DEFOVERHEADVCPUS_CLH@|0.2|g' \
        -e 's|@DEFMAXVCPUS@|0|g' \
        -e 's|@DEFMEMSZ@|2048|g' \
        -e 's|@DEFOVERHEADMEMSZ_CLH@|32|g' \
        -e 's|@DEFBRIDGES@|1|g' \
        -e 's|@DEFNETQUEUES@|1|g' \
        -e 's|@DEFSHAREDFS_CLH_VIRTIOFS@|virtio-fs|g' \
        -e 's|@DEFVIRTIOFSDAEMON@|/opt/kata/libexec/virtiofsd|g' \
        -e 's|@DEFVIRTIOFSCACHE@|auto|g' \
        -e 's|@DEFVIRTIOFSCACHESIZE@|0|g' \
        -e 's|@DEFVIRTIOFSQUEUESIZE@|1024|g' \
        -e 's|@DEFVIRTIOFSEXTRAARGS@|["--thread-pool-size=1", "-o", "announce_submounts"]|g' \
        -e 's|@DEFDISABLENESTEDVIRTUALIZATION_CLH@|false|g' \
        -e 's|@DEFNETWORKMODEL_CLH@|tcfilter|g' \
        -e 's|@DEFDISABLEGUESTSECCOMP@|true|g' \
        -e 's|@DEFSANDBOXCGROUPONLY_CLH@|true|g' \
        -e 's|@DEFSTATICRESOURCEMGMT_CLH@|true|g' \
        -e 's|@DEFEMPTYDIRMODE@|shared-fs|g' \
        -e 's|@DEFBINDMOUNTS@|[]|g' \
        -e 's|@DEFAULTEXPFEATURES@|[]|g' \
        -e 's|@DEFDANCONF@|/run/kata-containers/dans|g' \
        -e 's|@DEFCREATECONTAINERTIMEOUT@|30|g' \
        -e 's|@PIPESIZE@|1|g' \
        -e 's|@RUNTIMENAME@|virt_container|g' \
        -e 's|@HYPERVISOR_NAME_CLH@|clh|g' \
        -e 's|@PROJECT_NAME@|Kata Containers|g' \
        -e 's|@PROJECT_TYPE@|kata|g' \
        -e 's|@CONFIG_CLH_IN@|config/configuration-clh-runtime-rs.toml.in|g' \
        /tmp/configuration-clh-runtime-rs.toml.in > "$CLH_CONF"
    LEFTOVER="$(grep -oE '@[A-Z0-9_]+@' "$CLH_CONF" | sort -u | tr '\n' ' ' || true)"
    [ -z "$LEFTOVER" ] || { rm -f "$CLH_CONF"; fail "template placeholders not substituted: $LEFTOVER"; }
fi

# containerd resolves a shim by binary name on PATH: io.containerd.kata-clh.v2 looks for
# containerd-shim-kata-clh-v2
ln -sf "$KATA_SHIM" /usr/local/bin/containerd-shim-kata-clh-v2

# The shim does NOT pick its config from the name it was invoked under. It reads its own
# configuration.toml override first and the bundle default second, and the bundle ships that
# default as a symlink to the QEMU config — so without this override the node runs QEMU while
# every version string still reports cloud-hypervisor.
mkdir -p "$KATA_CONF_DIR"
ln -sf "$CLH_CONF" "$KATA_CONF_DIR/configuration.toml"
grep -q '^\[hypervisor\.clh\]' "$KATA_CONF_DIR/configuration.toml" \
    || fail "$KATA_CONF_DIR/configuration.toml does not configure cloud-hypervisor"
echo "  hypervisor      : $(/opt/kata/bin/cloud-hypervisor --version 2>&1 | head -n 1)"

step "XFS with project quotas for Docker's data root"
# --storage-opt size needs overlay2 on XFS mounted with pquota; ext4 silently refuses it.
# The same quota mechanism bounds workload volume mounts (VolumeMount.sizeLimitMb).
if ! mountpoint -q "$DOCKER_DATA"; then
    systemctl stop docker docker.socket 2>/dev/null || true
    [ -f "$DOCKER_FS_IMAGE" ] || truncate -s "$DOCKER_FS_SIZE" "$DOCKER_FS_IMAGE"
    blkid "$DOCKER_FS_IMAGE" >/dev/null 2>&1 || mkfs.xfs -q "$DOCKER_FS_IMAGE"
    mkdir -p "$DOCKER_DATA"
    mount -o loop,prjquota "$DOCKER_FS_IMAGE" "$DOCKER_DATA"
fi
findmnt -no FSTYPE,OPTIONS "$DOCKER_DATA" | sed 's/^/  /'
findmnt -no OPTIONS "$DOCKER_DATA" | grep -q prjquota || fail "$DOCKER_DATA is not mounted with prjquota"

# Persist it. Without an fstab entry the mount is lost on reboot, /var/lib/docker falls back to
# the ext4 root, and --storage-opt size stops working — the per-workload disk cap disappears
# with nothing to indicate it. RequiresMountsFor orders the mount before dockerd, which fstab
# alone does not guarantee.
grep -q "$DOCKER_FS_IMAGE" /etc/fstab || \
  printf '%s %s xfs loop,prjquota,x-systemd.before=docker.service 0 0\n' "$DOCKER_FS_IMAGE" "$DOCKER_DATA" >> /etc/fstab
mkdir -p /etc/systemd/system/docker.service.d
printf '[Unit]\nRequiresMountsFor=%s\n' "$DOCKER_DATA" > /etc/systemd/system/docker.service.d/10-kinotic-data-root.conf
findmnt --verify --fstab >/dev/null 2>&1 || fail "the fstab entry does not parse — a bad entry makes the node unbootable"

step "Bridge netfilter, so firewall rules actually see workload traffic"
# Without br_netfilter, traffic between two workloads on the docker bridge never enters
# iptables: rules are accepted, appear in the table, and are silently bypassed.
modprobe br_netfilter
sysctl -qw net.bridge.bridge-nf-call-iptables=1
printf 'br_netfilter\n' > /etc/modules-load.d/kinotic-bridge.conf
printf 'net.bridge.bridge-nf-call-iptables = 1\n' > /etc/sysctl.d/99-kinotic-bridge.conf
echo "  net.bridge.bridge-nf-call-iptables = $(sysctl -n net.bridge.bridge-nf-call-iptables)"

step "Docker daemon configuration"
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<EOF
{
  "data-root": "$DOCKER_DATA",
  "storage-driver": "overlay2",
  "live-restore": true,
  "icc": false,
  "runtimes": {
    "kata-clh": { "runtimeType": "io.containerd.kata-clh.v2" }
  }
}
EOF
# icc:false is the tenant boundary. Docker's default bridge otherwise forwards between every
# container on the node, so one customer's microVM can reach another's listening ports.
# live-restore keeps workloads running across a daemon restart; without it dockerd stops every
# container on shutdown and a vm-manager upgrade would kill every workload on the node.
systemctl daemon-reload
systemctl enable --now docker >/dev/null 2>&1 || true
systemctl restart docker
sleep 5
systemctl is-active --quiet docker || fail "docker is not running; journalctl -u docker -n 50"
docker info 2>/dev/null | grep -iE "Server Version|Storage Driver|Backing Filesystem|Live Restore|Runtimes" | sed 's/^/  /'

step "Host firewall floor"
install -m 0755 "$HERE/kinotic-node-firewall" /usr/local/sbin/kinotic-node-firewall
cat > /etc/systemd/system/kinotic-node-firewall.service <<'UNIT'
[Unit]
Description=Kinotic workload firewall floor (Azure IMDS, WireServer, egress default-deny)
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/usr/local/sbin/kinotic-node-firewall

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable --now kinotic-node-firewall >/dev/null 2>&1
systemctl restart kinotic-node-firewall
iptables -S DOCKER-USER | sed 's/^/  /'

step "Verifying the node"
"$HERE/verify-node.sh"

echo
echo "SETUP OK — kata $KATA_VERSION on cloud-hypervisor, docker $(docker --version | awk '{print $3}' | tr -d ,), overlay2 on xfs+prjquota"
echo "Egress default-deny is $( [ -e /etc/kinotic/egress-default-deny ] && echo ENABLED || echo 'DISABLED — see README, enable once the vm-manager writes per-workload egress rules' )"
