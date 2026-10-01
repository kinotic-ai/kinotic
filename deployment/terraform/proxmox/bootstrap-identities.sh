#!/usr/bin/env bash
# Creates the identities an empty cluster needs before anyone can sign in: the first system user,
# and a SYSTEM machine for each node's vm-manager. The production-profile migration seeds neither.
#
#   bootstrap-identities.sh <admin email> [<user@node>=<node id>...]
#
#   bootstrap-identities.sh you@example.com navid@192.168.1.11=dev-node-1 kinotic@192.168.1.30=dev-node-2
#
# Only bcrypt hashes reach Elasticsearch. The admin's password is written to a 0600 temporary file
# for the operator, whose path is printed; each machine's credentials go straight into its node's
# /etc/kinotic/vm-manager.secrets.env and are never written locally. Each node's vm-manager is
# restarted with its new credentials.
#
# Runs from the machine that applies this root: terraform, htpasswd, ssh to the host and the nodes.
set -euo pipefail

ADMIN_EMAIL="${1:?usage: $0 <admin email> [<user@node>=<node id>...]}"
shift
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST="$(terraform -chdir="$HERE" output -raw proxmox_host)"
ES="$(terraform -chdir="$HERE" output -raw elasticsearch_url)"
NOW="$(date -u +%Y-%m-%dT%H:%M:%S.000Z)"
umask 077

secret() { openssl rand -base64 48 | tr -d '/+=' | cut -c1-"$1"; }
bcrypt() { htpasswd -bnBC 12 "" "$1" | tr -d ':\n'; }
es() { ssh "root@$HOST" "curl -sf -H 'Content-Type: application/json' $*"; }

# A participant identity and its credential, as the bulk API takes them
rows() {   # <id> <secret> <identity json>
  python3 - "$1" "$(bcrypt "$2")" "$3" <<'PY'
import json, sys
_id, secret_hash, identity = sys.argv[1:]
print(json.dumps({"index": {"_index": "kinotic_participant_identity", "_id": _id}}))
print(identity)
print(json.dumps({"index": {"_index": "kinotic_identity_credential", "_id": _id}}))
print(json.dumps({"id": _id, "secretHash": secret_hash}))
PY
}

bulk() {
  ssh "root@$HOST" "curl -sf -H 'Content-Type: application/x-ndjson' -X POST '$ES/_bulk?refresh=true' --data-binary @-" \
    | python3 -c 'import sys, json; r = json.load(sys.stdin); sys.exit("bulk write failed: " + json.dumps(r)) if r["errors"] else None'
}

# A second run against a populated cluster would leave two system users with one email
existing="$(es "'$ES/kinotic_participant_identity/_count' -d '{\"query\":{\"bool\":{\"filter\":{\"term\":{\"email\":\"$ADMIN_EMAIL\"}},\"must_not\":{\"exists\":{\"field\":\"organizationId\"}}}}}'" \
  | python3 -c 'import sys, json; print(json.load(sys.stdin)["count"])')"
if [[ "$existing" != "0" ]]; then
  echo "A system user $ADMIN_EMAIL already exists; this is for an empty cluster" >&2
  exit 1
fi

admin_id="$(uuidgen | tr '[:upper:]' '[:lower:]')"
admin_password="$(secret 24)"
rows "$admin_id" "$admin_password" \
  "{\"id\":\"$admin_id\",\"type\":\"USER\",\"email\":\"$ADMIN_EMAIL\",\"displayName\":\"$ADMIN_EMAIL\",\"authType\":\"LOCAL\",\"enabled\":true,\"created\":\"$NOW\",\"updated\":\"$NOW\"}" \
  | bulk
password_file="$(mktemp -t kinotic-system-admin)"
echo "$admin_password" > "$password_file"
echo "==> System user $ADMIN_EMAIL; its password is in $password_file, delete it once saved"

for arg in "$@"; do
  target="${arg%%=*}"
  node="${arg#*=}"
  machine_id="$(uuidgen | tr '[:upper:]' '[:lower:]')"
  machine_secret="$(secret 40)"
  rows "$machine_id" "$machine_secret" \
    "{\"id\":\"$machine_id\",\"type\":\"MACHINE\",\"machineKind\":\"CLIENT\",\"displayName\":\"$node vm-manager\",\"authType\":\"CLIENT_CREDENTIALS\",\"enabled\":true,\"created\":\"$NOW\",\"updated\":\"$NOW\"}" \
    | bulk
  printf 'KINOTIC_CLIENT_ID=%s\nKINOTIC_CLIENT_SECRET=%s\n' "$machine_id" "$machine_secret" \
    | ssh "$target" 'sudo tee /etc/kinotic/vm-manager.secrets.env >/dev/null && sudo chmod 0600 /etc/kinotic/vm-manager.secrets.env && sudo systemctl restart kinotic-vm-manager'
  echo "==> Machine for $node installed on $target, its vm-manager restarted"
done
