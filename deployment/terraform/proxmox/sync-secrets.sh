#!/usr/bin/env bash
# Copies a secrets directory (generate-secrets.sh) to the host's secrets directory, each
# server's directory owned by the user the servers' containers run as, and restarts the
# containers that read it: the applier for the servers and Grafana, whose env files it merges,
# and a reboot for each server, which reads its mounted directory itself. The certificates
# certbot placed on the host are left alone.
#
#   sync-secrets.sh <secrets dir> [host]      host defaults to the root's proxmox_host output
set -euo pipefail

SRC="${1:?usage: $0 <secrets dir> [host]}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST="${2:-$(terraform -chdir="$HERE" output -raw proxmox_host)}"
DEST="${SECRETS_DIR:-/etc/kinotic/secrets}"
SNIPPETS="${SNIPPETS_DIR:-/var/lib/vz/snippets}"

# Ownership and modes are set on the host: -a would carry the operator's uid across, and
# macOS's openrsync takes no octal --chmod
rsync -rlt "$SRC/" "root@$HOST:$DEST/"
# cnb, uid 1002 and gid 1001 in the servers' containers, is 101002:101001 on the host
ssh "root@$HOST" "
  chmod -R u=rwX,go= '$DEST'
  chown -R 0:0 '$DEST'
  for dir in '$DEST'/kinotic-server-*; do
    [ -d \"\$dir\" ] && chown -R 101002:101001 \"\$dir\"
  done
  for manifest in '$SNIPPETS'/kinotic-kinotic-server-*.manifest.json '$SNIPPETS/kinotic-grafana.manifest.json'; do
    [ -e \"\$manifest\" ] && python3 '$SNIPPETS/kinotic-apply-container.py' \"\$manifest\"
  done
  # Each server reads secrets.yml and its key set from the mount, which the applier does not watch
  for manifest in '$SNIPPETS'/kinotic-kinotic-server-*.manifest.json; do
    server=\$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[\"vmid\"])' \"\$manifest\" 2>/dev/null)
    [ -n \"\$server\" ] && pct status \"\$server\" | grep -q running && pct reboot \"\$server\"
  done
  true
"
