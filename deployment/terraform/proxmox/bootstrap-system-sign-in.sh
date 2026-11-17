#!/usr/bin/env bash
# Writes the system console's sign-in registration, the SYSTEM row of kinotic_oidc_configuration the Azure
# root's system_sign_in output describes, so system users sign in with the account of the directory it names.
# The row holds no secret: it names the client secret the Azure root placed in the servers' key vault.
#
#   bootstrap-system-sign-in.sh
#
# Safe to run again: it replaces the row, which takes effect on the next sign-in.
#
# Runs from the machine that applies this root: terraform, ssh to the host.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AZURE="$HERE/../azure/dev-server"
HOST="$(terraform -chdir="$HERE" output -raw proxmox_host)"
ES="$(terraform -chdir="$HERE" output -raw elasticsearch_url)"

row="$(terraform -chdir="$AZURE" output -json system_sign_in | python3 -c '
import sys, json, datetime
row = json.load(sys.stdin)
now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
row.update(type="SYSTEM", enabled=True, created=now, updated=now)
print(json.dumps(row))')"
id="$(python3 -c 'import sys, json; print(json.loads(sys.argv[1])["id"])' "$row")"

printf '%s' "$row" | ssh "root@$HOST" "curl -sf -H 'Content-Type: application/json' -X PUT '$ES/kinotic_oidc_configuration/_doc/$id?refresh=true' --data-binary @-" >/dev/null
echo "==> The system console signs in through $id"
