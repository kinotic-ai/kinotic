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
# Pinned rather than resolved: a node's runtime is a decision, not whatever upstream released
# most recently. 4.1.0 is the floor as well as the default — it is the first release carrying
# the fix for CVE-2026-77176, which affects every version up to 4.0.0. Bumping is an edit here,
# made against the release notes and the advisories for the version being left behind.
KATA_VERSION="${KATA_VERSION:-4.1.0}"

[ "$(id -u)" -eq 0 ] || fail "run as root (sudo $0)"
[ -e /dev/kvm ] || fail "/dev/kvm is missing — this host has no nested virtualization"

step "Base packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl jq tar zstd xfsprogs ca-certificates iptables ipset >/dev/null

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
# amd64 only. A CLOUD_HYPERVISOR node needs nested virtualization, which Azure offers on its
# x86 sizes and not on its arm64 ones; kata does not build cloud-hypervisor support for
# aarch64 either, and a guest there cannot see a hot-plugged NIC. See NOTES.md.
[ "$(uname -m)" = "x86_64" ] || fail "$(uname -m) cannot host a Cloud Hypervisor node — see NOTES.md"
ASSET="kata-static-$KATA_VERSION-amd64.tar.zst"
KATA_URL="https://github.com/kata-containers/kata-containers/releases/download/$KATA_VERSION/$ASSET"
echo "  kata-containers : $KATA_VERSION ($ASSET)"
curl -fsSL -o "/tmp/$ASSET" "$KATA_URL" || fail "no kata-static amd64 asset for $KATA_VERSION"
# Removed rather than unpacked over: tar does not delete what a previous release left, and the
# releases do not ship the same set of files, so a node that has been provisioned before would
# otherwise keep files from every release it has ever installed — and the runtime it runs would
# be a fact about that history rather than about the version named above.
rm -rf /opt/kata
tar --use-compress-program=unzstd -xf "/tmp/$ASSET" -C /
# The bundle ships cloud-hypervisor/firecracker 0744, unusable by a non-root caller
chmod 0755 /opt/kata/bin/cloud-hypervisor /opt/kata/bin/firecracker 2>/dev/null || true

# Kata shipped a Go runtime and runtime-rs side by side until 4.1.0 dropped the Go one on every
# architecture. Only runtime-rs remains, and it keeps its own config tree: the shim reads
# /etc/kata-containers/runtime-rs/configuration.toml, and its defaults are the
# configuration-*-runtime-rs.toml files rather than the ones beside them.
KATA_SHIM=/opt/kata/runtime-rs/bin/containerd-shim-kata-v2
KATA_CONF_DIR=/etc/kata-containers/runtime-rs
CLH_CONF=/opt/kata/share/defaults/kata-containers/runtime-rs/configuration-clh-runtime-rs.toml
[ -x "$KATA_SHIM" ] || fail "$KATA_VERSION provided no runtime-rs shim at $KATA_SHIM"
echo "  shim            : $KATA_SHIM"

[ -f "$CLH_CONF" ] || fail "$KATA_VERSION ships no Cloud Hypervisor configuration at $CLH_CONF"

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
Description=Kinotic workload firewall floor (Azure IMDS, WireServer, workload resolver, egress default-deny)
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

step "Workload resolver (dnsmasq on the bridge, filling per-hostname ipsets)"
# Every workload is given the bridge address as its only resolver, so an address a guest
# connects to by name is one this dnsmasq answered. The vm-manager writes ipset= directives
# for the names workloads are allowed into /etc/dnsmasq.d/kinotic-egress.conf and restarts
# the service when they change; this is the static half.
BRIDGE_ADDRESS="$(docker network inspect bridge -f '{{range .IPAM.Config}}{{.Gateway}}{{end}}')"
[ -n "$BRIDGE_ADDRESS" ] || fail "the docker bridge has no gateway address"
[ -r /run/systemd/resolve/resolv.conf ] || fail "systemd-resolved is not running, so there is no upstream resolver list for dnsmasq to follow"
mkdir -p /etc/dnsmasq.d
cat > /etc/dnsmasq.d/kinotic-node.conf <<EOF
# Workload resolver, written by setup-node.sh. The vm-manager writes kinotic-egress.conf
# beside this file and restarts dnsmasq when it changes.
#
# Only the bridge address is bound, and bound dynamically: systemd-resolved holds the
# loopback port, and docker0 may come up after dnsmasq has.
listen-address=$BRIDGE_ADDRESS
bind-dynamic
# Upstream is whatever the node itself resolves through — the VNet resolver on Azure — read
# from the file systemd-resolved keeps current rather than the stub in /etc/resolv.conf.
resolv-file=/run/systemd/resolve/resolv.conf
# dnsmasq writes an answer into an ipset only while processing an upstream reply, never when
# answering from its own cache, so every answer has to come from upstream.
cache-size=0
# The node's /etc/hosts names its own interfaces, which a guest has no business resolving.
no-hosts
EOF
if ! command -v dnsmasq >/dev/null 2>&1; then
    # The package starts the service on install with its stock configuration, which binds
    # port 53 on every address and fails against systemd-resolved; the policy hook holds the
    # start back until the configuration above is what it starts with
    printf '#!/bin/sh\nexit 101\n' > /usr/sbin/policy-rc.d
    chmod 0755 /usr/sbin/policy-rc.d
    apt-get install -y -qq dnsmasq >/dev/null || { rm -f /usr/sbin/policy-rc.d; fail "dnsmasq did not install"; }
    rm -f /usr/sbin/policy-rc.d
fi
# With the resolvconf package present the service wrapper would otherwise hand dnsmasq a
# resolv file of its own and register 127.0.0.1 as the node's resolver, which it does not serve
sed -i 's/^#\?IGNORE_RESOLVCONF=.*/IGNORE_RESOLVCONF=yes/' /etc/default/dnsmasq
grep -q '^IGNORE_RESOLVCONF=yes' /etc/default/dnsmasq || echo 'IGNORE_RESOLVCONF=yes' >> /etc/default/dnsmasq
systemctl enable dnsmasq >/dev/null 2>&1 || true
systemctl restart dnsmasq
systemctl is-active --quiet dnsmasq || fail "dnsmasq is not running; journalctl -u dnsmasq -n 50"
echo "  listening on    : $BRIDGE_ADDRESS:53 (give the vm-manager KINOTIC_WORKLOAD_DNS=$BRIDGE_ADDRESS)"
echo "  upstream        : $(grep -E '^nameserver' /run/systemd/resolve/resolv.conf | awk '{print $2}' | paste -sd ' ')"

step "Verifying the node"
"$HERE/verify-node.sh"

echo
echo "SETUP OK — kata $KATA_VERSION on cloud-hypervisor, docker $(docker --version | awk '{print $3}' | tr -d ,), overlay2 on xfs+prjquota"
echo "Egress default-deny is $( [ -e /etc/kinotic/egress-default-deny ] && echo ENABLED || echo 'DISABLED — see README, enable once the vm-manager writes per-workload egress rules' )"
