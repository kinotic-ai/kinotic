#!/bin/bash
# Dumps the license key server's database and uploads the dump to the Azure container named in
# licenses-db-backup.env (sync-secrets.sh), as licenses-<UTC time>.sql.gz. The upload is checked
# against the dump's size, and a failure at any step fails the unit.
#
# Restore, into an empty licenses database with the license key server stopped:
#   gunzip -c licenses-<time>.sql.gz | pct exec <postgres vmid> -- psql -U licenses -d licenses
set -euo pipefail

SECRETS_DIR="${SECRETS_DIR:-/etc/kinotic/secrets}"
# read, not sourced: a SAS joins its fields with &, which a shell takes as a control operator
setting() { sed -n "s/^$1=//p" "$SECRETS_DIR/licenses-db-backup.env"; }
container_url=$(setting LICENSES_DB_BACKUP_CONTAINER_URL)
sas=$(setting LICENSES_DB_BACKUP_SAS)
[ -n "$container_url" ] && [ -n "$sas" ] || { echo "licenses-db-backup.env names no destination" >&2; exit 1; }

vmid=$(pct list | awk '$NF == "postgres" { print $1 }')
[ -n "$vmid" ] || { echo "no postgres container" >&2; exit 1; }

dump=$(mktemp)
trap 'rm -f "$dump"' EXIT
pct exec "$vmid" -- pg_dump -U licenses -d licenses --no-owner | gzip > "$dump"
# an empty schema still dumps to several hundred bytes; less is a dump that did not run
[ "$(stat -c %s "$dump")" -gt 200 ] || { echo "the dump is empty" >&2; exit 1; }

name="licenses-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
curl -sS --fail-with-body -X PUT -H 'x-ms-blob-type: BlockBlob' -H 'x-ms-version: 2021-08-06' \
     --data-binary @"$dump" "$container_url/$name?$sas" >/dev/null
echo "uploaded $name, $(stat -c %s "$dump") bytes"
