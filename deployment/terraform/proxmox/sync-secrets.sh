#!/usr/bin/env bash
# Copies a secrets directory (generate-secrets.sh) to the host's secrets directory, owned by
# the user the server's container runs as, and re-applies the containers that read it. The
# certificates certbot placed on the host are left alone.
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
# cnb, uid 1002 and gid 1001 in the server's container, is 101002:101001 on the host
ssh "root@$HOST" "
  chmod -R u=rwX,go= '$DEST'
  chown -R 0:0 '$DEST'
  chown -R 101002:101001 '$DEST/kinotic-server'
  for manifest in '$SNIPPETS/kinotic-kinotic-server.manifest.json' '$SNIPPETS/kinotic-grafana.manifest.json'; do
    [ -e \"\$manifest\" ] && python3 '$SNIPPETS/kinotic-apply-container.py' \"\$manifest\"
  done
  true
"
