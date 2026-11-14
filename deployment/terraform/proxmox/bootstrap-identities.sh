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
# restarted with its new credentials, and the system server is restarted last: the seed job that
# places the identities in the authorization graph runs as it starts.
#
# The machines are written before the system user, which the seed job reads first and takes as the
# sign that the identities are all there. The system user's identity is the last write, after its
# credential and the password file. A run that fails before it can be made again as it was: a
# machine's id is derived from its node's and the system user's from its email, so each is written
# over. When the write of that identity reports a failure the system user is read back: the run
# carries on when it is there, and otherwise stops with the password file kept, since the write may
# still land.
#
# ADMIN_SECRET_HASH, the bcrypt hash of a password the admin already has (its
# kinotic_identity_credential's secretHash, read before the cluster was emptied), keeps that
# password; no password file is written.
#
# Runs from the machine that applies this root: terraform, htpasswd, ssh to the host and the nodes.
set -euo pipefail

ADMIN_EMAIL="${1:?usage: $0 <admin email> [<user@node>=<node id>...]}"
shift
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST="$(terraform -chdir="$HERE" output -raw proxmox_host)"
ES="$(terraform -chdir="$HERE" output -raw elasticsearch_url)"
system_vmid="$(terraform -chdir="$HERE" output -json containers | python3 -c 'import sys, json; print(json.load(sys.stdin)["kinotic-server-system"])')"
NOW="$(date -u +%Y-%m-%dT%H:%M:%S.000Z)"
umask 077

secret() { openssl rand -base64 48 | tr -d '/+=' | cut -c1-"$1"; }
# The password goes to htpasswd on stdin, so it never appears in a process listing
bcrypt() { printf '%s' "$1" | htpasswd -niBC 12 "" | tr -d ':\n'; }
es() { ssh "root@$HOST" "curl -sf -H 'Content-Type: application/json' $*"; }

# A participant identity, and its credential, as the bulk API takes them
identity_row() {   # <id> <identity json>
  python3 - "$1" "$2" <<'PY'
import json, sys
_id, identity = sys.argv[1:]
print(json.dumps({"index": {"_index": "kinotic_participant_identity", "_id": _id}}))
print(identity)
PY
}

credential_row() {   # <id> <secret hash>
  python3 - "$1" "$2" <<'PY'
import json, sys
_id, secret_hash = sys.argv[1:]
print(json.dumps({"index": {"_index": "kinotic_identity_credential", "_id": _id}}))
print(json.dumps({"id": _id, "secretHash": secret_hash}))
PY
}

bulk() {
  ssh "root@$HOST" "curl -sf -H 'Content-Type: application/x-ndjson' -X POST '$ES/_bulk?refresh=true' --data-binary @-" \
    | python3 -c 'import sys, json; r = json.load(sys.stdin); sys.exit("bulk write failed: " + json.dumps(r)) if r["errors"] else None'
}

# How many system users have the admin's email
system_users() {
  es "'$ES/kinotic_participant_identity/_count' -d '{\"query\":{\"bool\":{\"filter\":[{\"term\":{\"type\":\"SYSTEM_USER\"}},{\"term\":{\"email\":\"$ADMIN_EMAIL\"}}]}}}'" \
    | python3 -c 'import sys, json; print(json.load(sys.stdin)["count"])'
}

existing="$(system_users)"
if [[ "$existing" != "0" ]]; then
  echo "A system user $ADMIN_EMAIL already exists; this is for an empty cluster" >&2
  exit 1
fi

for arg in "$@"; do
  target="${arg%%=*}"
  node="${arg#*=}"
  machine_id="$(python3 -c 'import sys, uuid; print(uuid.uuid5(uuid.NAMESPACE_URL, "kinotic:vm-manager:" + sys.argv[1]))' "$node")"
  machine_secret="$(secret 40)"
  {
    credential_row "$machine_id" "$(bcrypt "$machine_secret")"
    identity_row "$machine_id" \
      "{\"id\":\"$machine_id\",\"type\":\"MACHINE\",\"machineType\":\"CLIENT\",\"displayName\":\"$node vm-manager\",\"enabled\":true,\"created\":\"$NOW\",\"updated\":\"$NOW\"}"
  } | bulk
  printf 'KINOTIC_CLIENT_ID=%s\nKINOTIC_CLIENT_SECRET=%s\n' "$machine_id" "$machine_secret" \
    | ssh "$target" "sudo sh -c 'umask 077 && rm -f /etc/kinotic/vm-manager.secrets.env && cat > /etc/kinotic/vm-manager.secrets.env' && sudo systemctl restart kinotic-vm-manager"
  echo "==> Machine for $node installed on $target, its vm-manager restarted"
done

admin_id="$(python3 -c 'import sys, uuid; print(uuid.uuid5(uuid.NAMESPACE_URL, "kinotic:system-user:" + sys.argv[1]))' "$ADMIN_EMAIL")"
password_file=""
if [[ -n "${ADMIN_SECRET_HASH:-}" ]]; then
  admin_hash="$ADMIN_SECRET_HASH"
else
  admin_password="$(secret 24)"
  admin_hash="$(bcrypt "$admin_password")"
  # A full template, since GNU mktemp refuses the bare prefix BSD mktemp takes with -t
  password_file="$(mktemp "${TMPDIR:-/tmp}/kinotic-system-admin.XXXXXX")"
  echo "$admin_password" > "$password_file"
  # The identity is written after the credential, so a failed credential leaves no system user
  trap 'rm -f "$password_file"' ERR
fi
# Two requests, since one bulk request can write the identity and fail the credential: the identity
# is what the check for an existing system user reads, so it is written once the credential is held
credential_row "$admin_id" "$admin_hash" | bulk
trap - ERR
# The identity can be written with the answer lost on its way back, and can land after the count that
# follows a failure, so the password file outlives every failure of this write
if ! identity_row "$admin_id" \
  "{\"id\":\"$admin_id\",\"type\":\"SYSTEM_USER\",\"email\":\"$ADMIN_EMAIL\",\"displayName\":\"$ADMIN_EMAIL\",\"enabled\":true,\"created\":\"$NOW\",\"updated\":\"$NOW\"}" \
  | bulk; then
  if [[ "$(system_users || true)" != "1" ]]; then
    echo "The write of the system user $ADMIN_EMAIL reported a failure, and the system user is not there to read. Run this again: if that run finds the system user there, restart the system server (container $system_vmid) by hand, since its seed job places the identities as it starts" >&2
    if [[ -n "$password_file" ]]; then
      echo "Keep $password_file until then: it holds the system user's password if that run finds the system user there. A run that succeeds writes a new password, and this file can be deleted" >&2
    fi
    exit 1
  fi
fi
if [[ -n "${ADMIN_SECRET_HASH:-}" ]]; then
  echo "==> System user $ADMIN_EMAIL, with the password it already had"
else
  echo "==> System user $ADMIN_EMAIL; its password is in $password_file, delete it once saved"
fi

# The system server was running before these identities existed, and its seed job runs as it starts.
# kinotic-keepalive.timer may start the stopped container first, which the start then finds running.
if ! ssh "root@$HOST" "pct stop '$system_vmid' && { pct start '$system_vmid' || pct status '$system_vmid' | grep -q running; }"; then
  echo "The identities are written, but the system server (container $system_vmid) was not restarted; restart it by hand, since its seed job places them as it starts" >&2
  exit 1
fi
echo "==> System server restarted; its seed job makes $ADMIN_EMAIL a system administrator and each machine a node registrar"
