#!/usr/bin/env bash
# Places the secret files the operator keychain holds (../dev-server-secret-file.sh) in the host's
# secrets directory, each server's directory owned by the user its container runs as, and restarts
# the containers that read them: the applier for the servers, the license key server and
# Postgres, whose env files it merges, and a reboot for each server, which reads its mounted
# directory itself. The Cloudflare DNS token in kinotic-servers.env is also written into the
# credentials file certbot's dns-cloudflare plugin and kinotic-dyndns read, so the host's
# certificates and records follow the same token. The certificates certbot placed on the host are
# left alone.
#
#   sync-secrets.sh [host]      host defaults to the root's proxmox_host output
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST="${1:-$(terraform -chdir="$HERE" output -raw proxmox_host)}"
FILES="$HERE/../dev-server-secret-file.sh"
DEST="${SECRETS_DIR:-/etc/kinotic/secrets}"
SNIPPETS="${SNIPPETS_DIR:-/var/lib/vz/snippets}"

# Each file goes from the keychain to the host over ssh, and is never a file of this Mac
"$FILES" list | while IFS= read -r path; do
  "$FILES" get "$path" | ssh "root@$HOST" "install -D -m 0600 /dev/stdin '$DEST/$path'"
done
# cnb, uid 1002 and gid 1001 in the servers' containers, is 101002:101001 on the host
ssh "root@$HOST" "
  chmod -R u=rwX,go= '$DEST'
  chown -R 0:0 '$DEST'
  for dir in '$DEST'/kinotic-server-* '$DEST/summit-42'; do
    # certbot's deploy hook installs each server's certificate into its certs directory
    [ -d \"\$dir\" ] && mkdir -p \"\$dir/certs\" && chown -R 101002:101001 \"\$dir\"
  done
  # licenses, uid and gid 10001 in the license key server's container; haproxy, 99, in the edge's
  [ -d '$DEST/license-key-server' ] && chown -R 110001:110001 '$DEST/license-key-server'
  [ -d '$DEST/edge' ] && chown -R 100099:100099 '$DEST/edge'
  token=\$(sed -n 's/^KINOTIC_DOMAIN_PUBLISHEDSITES_CLOUDFLARE_APITOKEN=//p' '$DEST/kinotic-servers.env')
  [ -n \"\$token\" ] && printf 'dns_cloudflare_api_token = %s\n' \"\$token\" | install -m 0600 /dev/stdin /etc/kinotic/certbot-cloudflare.ini
  for manifest in '$SNIPPETS'/kinotic-kinotic-server-*.manifest.json '$SNIPPETS/kinotic-summit-42.manifest.json' '$SNIPPETS/kinotic-postgres.manifest.json' '$SNIPPETS/kinotic-license-key-server.manifest.json'; do
    [ -e \"\$manifest\" ] && python3 '$SNIPPETS/kinotic-apply-container.py' \"\$manifest\"
  done
  # Each server reads secrets.yml and its key set from the mount, which the applier does not watch
  for manifest in '$SNIPPETS'/kinotic-kinotic-server-*.manifest.json '$SNIPPETS/kinotic-summit-42.manifest.json'; do
    server=\$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[\"vmid\"])' \"\$manifest\" 2>/dev/null)
    [ -n \"\$server\" ] && pct status \"\$server\" | grep -q running && pct reboot \"\$server\"
  done
  true
"
