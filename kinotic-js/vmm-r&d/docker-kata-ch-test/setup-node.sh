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
KATA_JSON="$(curl -fsSL https://api.github.com/repos/kata-containers/kata-containers/releases/latest)"
KATA_VERSION="$(printf '%s' "$KATA_JSON" | jq -r .tag_name)"
# The release has shipped kata-static as .tar.xz and as .tar.zst at different times, so the
# asset is picked out of the release JSON rather than constructed
KATA_URL="$(printf '%s' "$KATA_JSON" | jq -r '.assets[] | select(.name | startswith("kata-static-") and contains("-amd64.tar")) | .browser_download_url' | head -n 1)"
[ -n "$KATA_URL" ] || fail "release $KATA_VERSION has no kata-static amd64 asset"
ASSET="${KATA_URL##*/}"
echo "  kata-containers : $KATA_VERSION ($ASSET)"
curl -fsSL -o "/tmp/$ASSET" "$KATA_URL"
case "$ASSET" in
    *.tar.zst) tar --use-compress-program=unzstd -xf "/tmp/$ASSET" -C / ;;
    *.tar.xz)  tar -xJf "/tmp/$ASSET" -C / ;;
    *) fail "unhandled archive format for $ASSET" ;;
esac
[ -x /opt/kata/bin/containerd-shim-kata-v2 ] || fail "the bundle provided no kata shim"
# The bundle ships cloud-hypervisor/firecracker 0744, unusable by a non-root caller
chmod 0755 /opt/kata/bin/cloud-hypervisor /opt/kata/bin/firecracker 2>/dev/null || true

# containerd resolves a shim by binary name on PATH: io.containerd.kata-clh.v2 looks for
# containerd-shim-kata-clh-v2
ln -sf /opt/kata/bin/containerd-shim-kata-v2 /usr/local/bin/containerd-shim-kata-clh-v2

# The shim does NOT pick its config from the name it was invoked under. kata-runtime reads
# /etc/kata-containers/configuration.toml first and the bundle default second, and the bundle
# ships that default as a symlink to configuration-qemu.toml — so without this override the
# node runs QEMU while every version string still reports cloud-hypervisor.
CLH_CONF=/opt/kata/share/defaults/kata-containers/configuration-clh.toml
[ -f "$CLH_CONF" ] || fail "no Cloud Hypervisor configuration in this bundle"
mkdir -p /etc/kata-containers
ln -sf "$CLH_CONF" /etc/kata-containers/configuration.toml
/opt/kata/bin/kata-runtime env 2>/dev/null | grep -m1 'Version = "cloud-hypervisor' | sed 's/^/  /'

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
