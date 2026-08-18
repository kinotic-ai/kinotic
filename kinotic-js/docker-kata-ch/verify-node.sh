#!/usr/bin/env bash
# Asserts every invariant setup-node.sh establishes. Run it after a reboot, after a Docker
# upgrade, or any time you need to know whether this node is still safe to place customer
# workloads on.
#
# These are the checks the vm-manager should make before registering — a node whose data root
# fell back to ext4 still runs workloads, it just stops enforcing their disk caps, and a node
# whose firewall chain was flushed still runs workloads, it just stops hiding Azure IMDS from
# them. Both fail silently, which is why they are asserted rather than assumed.
set -uo pipefail
PASS=0; FAIL=0
ok()   { printf '  \033[32m✓\033[0m %-46s %s\n' "$1" "${2:-}"; PASS=$((PASS+1)); }
bad()  { printf '  \033[31m✗\033[0m %-46s %s\n' "$1" "${2:-}"; FAIL=$((FAIL+1)); }
check() { if [ "$1" = "$2" ]; then ok "$3" "$4"; else bad "$3" "expected $2, got $1"; fi; }

DOCKER_DATA=/var/lib/docker

echo "Node invariants"

# --- the runtime is present and is really Cloud Hypervisor --------------------------------
[ -e /dev/kvm ] && ok "nested virtualization" "/dev/kvm present" || bad "nested virtualization" "/dev/kvm missing"
systemctl is-active --quiet docker && ok "docker" "active" || bad "docker" "not active"
[ -L /etc/kata-containers/configuration.toml ] \
  && case "$(readlink -f /etc/kata-containers/configuration.toml)" in
       *clh*) ok "kata config" "-> $(basename "$(readlink -f /etc/kata-containers/configuration.toml)")" ;;
       *) bad "kata config" "resolves to $(readlink -f /etc/kata-containers/configuration.toml), not the clh one — this node would run QEMU" ;;
     esac \
  || bad "kata config" "/etc/kata-containers/configuration.toml is not a symlink"
[ -x /usr/local/bin/containerd-shim-kata-clh-v2 ] && ok "kata-clh shim on PATH" || bad "kata-clh shim on PATH" "missing"
docker info 2>/dev/null | grep -q 'kata-clh' && ok "kata-clh runtime registered" || bad "kata-clh runtime registered" "not in docker info"

# --- storage: the per-workload disk cap depends on all three of these ----------------------
FS="$(findmnt -no FSTYPE "$DOCKER_DATA" 2>/dev/null)"
check "$FS" "xfs" "data root filesystem" "$DOCKER_DATA"
findmnt -no OPTIONS "$DOCKER_DATA" 2>/dev/null | grep -q prjquota \
  && ok "project quotas" "prjquota" || bad "project quotas" "not mounted with prjquota — disk caps silently stop working"
grep -q "$DOCKER_DATA" /etc/fstab \
  && ok "data root persisted" "fstab entry present" || bad "data root persisted" "no fstab entry — the mount is lost on reboot"
DRIVER="$(docker info -f '{{.Driver}}' 2>/dev/null)"
check "$DRIVER" "overlay2" "storage driver" ""

# --- network policy plumbing ---------------------------------------------------------------
check "$(sysctl -n net.bridge.bridge-nf-call-iptables 2>/dev/null)" "1" "bridge netfilter" "iptables sees bridged traffic"
ICC="$(docker network inspect bridge -f '{{index .Options "com.docker.network.bridge.enable_icc"}}' 2>/dev/null)"
check "${ICC:-true}" "false" "inter-container comms" "tenant to tenant"

for rule in "-d 169.254.169.254/32 -j DROP" \
            "-d 168.63.129.16/32 -p tcp --dport 80 -j DROP" \
            "-d 168.63.129.16/32 -p tcp --dport 32526 -j DROP"; do
    # shellcheck disable=SC2086
    if iptables -C DOCKER-USER $rule >/dev/null 2>&1; then
        ok "firewall floor" "${rule%% -j*}"
    else
        bad "firewall floor" "missing: $rule"
    fi
done
systemctl is-enabled --quiet kinotic-node-firewall 2>/dev/null \
  && ok "firewall floor persisted" "kinotic-node-firewall enabled" \
  || bad "firewall floor persisted" "unit not enabled — rules are lost on reboot"

if [ -e /etc/kinotic/egress-default-deny ]; then
    SUBNET="$(docker network inspect bridge -f '{{range .IPAM.Config}}{{.Subnet}}{{end}}' 2>/dev/null)"
    iptables -C DOCKER-USER -s "$SUBNET" -j DROP >/dev/null 2>&1 \
      && ok "egress default-deny" "$SUBNET" || bad "egress default-deny" "marker present but rule missing"
else
    printf '  \033[33m—\033[0m %-46s %s\n' "egress default-deny" "off (enable once per-workload rules ship)"
fi

echo
if [ "$FAIL" -eq 0 ]; then
    echo "NODE OK — $PASS checks passed"
else
    echo "NODE NOT FIT FOR WORKLOADS — $FAIL of $((PASS+FAIL)) checks failed" >&2
    exit 1
fi
