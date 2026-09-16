#!/usr/bin/env bash
# Prepares a freshly installed Proxmox host for deployment/terraform/proxmox, once, as root:
#
#   - a ZFS pool on each Elasticsearch disk, with the dataset the node's container mounts
#     (es_data_dirs: /es1/data, /es2/data, /es3/data), so a disk failure is contained to the
#     node that owns it; a disk that already carries the pool is imported, never recreated
#   - the directories the other containers keep config and state in (data_dir) and the one
#     the operator places secrets in (secrets_dir)
#   - vm.max_map_count for Elasticsearch, which shares the host's kernel
#   - OCI images and uploaded files on the local datastore
#   - kinotic-keepalive.timer, which restarts a container whose entrypoint exited
#   - kinotic-dyndns.timer, which keeps the certificate's hostnames on the router's current
#     public address (host/kinotic-dyndns.py, beside this script)
#   - the host's own exposure: SSH by key only, rpcbind off, and the Proxmox firewall admitting
#     SSH and the web UI from the LAN alone
#
#   prepare-host.sh /dev/disk/by-id/<es disk 1> /dev/disk/by-id/<es disk 2> /dev/disk/by-id/<es disk 3>
set -euo pipefail
fail() { echo "PREPARE FAILED: $*" >&2; exit 1; }

DATA_DIR="${DATA_DIR:-/var/lib/kinotic}"
SECRETS_DIR="${SECRETS_DIR:-/etc/kinotic/secrets}"
FILES_DATASTORE="${FILES_DATASTORE:-local}"

[ "$(id -u)" -eq 0 ] || fail "run as root"
[ $# -eq 3 ] || fail "usage: $0 <es disk 1> <es disk 2> <es disk 3>   (whole disks, by stable id)"

# The applier is python, the secrets arrive by rsync, the applier's checks use curl
apt-get install -y --no-install-recommends python3 rsync curl >/dev/null

i=0
for disk in "$@"; do
    i=$((i + 1))
    pool="es$i"
    [ -b "$disk" ] || fail "$disk is not a block device"
    if zpool list -H -o name "$pool" >/dev/null 2>&1; then
        echo "$pool: present"
    elif zpool import "$pool" >/dev/null 2>&1; then
        echo "$pool: imported"
    elif blkid -p "$disk" >/dev/null 2>&1; then
        fail "$disk carries a partition table or filesystem; if it is expendable, wipe it first: wipefs -a $disk"
    else
        zpool create -o ashift=12 -O compression=lz4 -O atime=off -O xattr=sa -m none "$pool" "$disk"
        zfs create -o mountpoint="/$pool/data" "$pool/data"
        echo "$pool: created on $disk"
    fi
    mountpoint -q "/$pool/data" || fail "/$pool/data is not mounted"
done

mkdir -p "$DATA_DIR/config" "$DATA_DIR/data" "$DATA_DIR/state/keepalive" "$SECRETS_DIR" /var/log/kinotic
chmod 0700 "$SECRETS_DIR"

echo 'vm.max_map_count = 262144' > /etc/sysctl.d/90-kinotic-elasticsearch.conf
sysctl -q -p /etc/sysctl.d/90-kinotic-elasticsearch.conf

# Each container's console log, which LXC keeps open: 16 MB, one rotation
cat > /etc/logrotate.d/kinotic <<'CONF'
/var/log/kinotic/*.log {
    size 16M
    rotate 1
    copytruncate
    missingok
    notifempty
}
CONF

# OCI images are vztmpl content, the manifests and config files snippets
current=$(awk -v ds="$FILES_DATASTORE" '$1 ~ /:$/ { in_ds = ($2 == ds) } in_ds && $1 == "content" { print $2 }' /etc/pve/storage.cfg)
wanted=$(printf '%s\n' "${current//,/$'\n'}" snippets vztmpl | grep -v '^$' | sort -u | paste -sd,)
[ "$current" = "$wanted" ] || pvesm set "$FILES_DATASTORE" --content "$wanted"

# Proxmox does not restart a container whose entrypoint exited; this does, for the containers
# kinotic-apply-container.py marked, every minute
cat > /usr/local/sbin/kinotic-keepalive <<'SH'
#!/bin/bash
for marker in /var/lib/kinotic/state/keepalive/*; do
    [ -e "$marker" ] || continue
    vmid="${marker##*/}"
    case "$(pct status "$vmid" 2>/dev/null)" in
        "status: stopped") [ -e "$marker" ] && pct start "$vmid" ;;
        "") rm -f "$marker" ;;
    esac
done
SH
chmod 0755 /usr/local/sbin/kinotic-keepalive
cat > /etc/systemd/system/kinotic-keepalive.service <<'UNIT'
[Unit]
Description=Start kinotic containers whose entrypoint exited

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/kinotic-keepalive
UNIT
cat > /etc/systemd/system/kinotic-keepalive.timer <<'UNIT'
[Unit]
Description=Start kinotic containers whose entrypoint exited, every minute

[Timer]
OnBootSec=2min
OnUnitActiveSec=1min

[Install]
WantedBy=timers.target
UNIT

# The ISP's address changes; the updater runs on certbot's python, with certbot's credentials
install -m 0755 "$(dirname "$0")/kinotic-dyndns.py" /usr/local/sbin/kinotic-dyndns
cat > /etc/systemd/system/kinotic-dyndns.service <<'UNIT'
[Unit]
Description=Point the certificate's hostnames at the current public address
After=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/kinotic-dyndns
UNIT
cat > /etc/systemd/system/kinotic-dyndns.timer <<'UNIT'
[Unit]
Description=Point the certificate's hostnames at the current public address, every 5 minutes

[Timer]
OnBootSec=3min
OnUnitActiveSec=5min

[Install]
WantedBy=timers.target
UNIT
systemctl daemon-reload
systemctl enable --now kinotic-keepalive.timer kinotic-dyndns.timer >/dev/null 2>&1

# The host holds every secret: root logs in by key alone, nothing here uses NFS, and the
# firewall admits only SSH and the web UI, from the LAN the host sits on
cat > /etc/ssh/sshd_config.d/kinotic.conf <<'CONF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
CONF
sshd -t && systemctl reload ssh
systemctl disable --now rpcbind.socket rpcbind.service >/dev/null 2>&1 || true
lan=$(ip -4 route show dev vmbr0 proto kernel scope link | awk '{print $1}' | head -1)
cat > /etc/pve/firewall/cluster.fw <<CONF
[OPTIONS]
enable: 1
policy_in: DROP
policy_out: ACCEPT

[RULES]
IN ACCEPT -source $lan -p tcp -dport 22 -log nolog
IN ACCEPT -source $lan -p tcp -dport 8006 -log nolog
IN ACCEPT -p icmp -log nolog
CONF
pve-firewall restart >/dev/null 2>&1 || true

echo "host prepared: es1..es3 mounted, $DATA_DIR and $SECRETS_DIR present, $FILES_DATASTORE holds $wanted"
