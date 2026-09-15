#!/usr/bin/env bash
# Generates what the development server needs that nothing else issues, in the layout
# sync-secrets.sh copies to the host's secrets directory. The kinotic-server directory is
# bind-mounted at /etc/kinotic in the server's container; the env files are merged into a
# container's environment by the applier on the host. Nothing here passes through terraform.
#
#   kinotic-server.env                        AZURE_CLIENT_SECRET, from the Azure root
#   kinotic-server/secrets.yml                the secret-storage master key, the GitHub App's
#                                             private key and webhook secret
#   kinotic-server/platform-secrets/jwt-signing-keys   the JWT key set, in the shape
#                                             deployment/kind/terraform/platform-secrets.tf
#                                             produces, so a key can be added at migration
#   kinotic-server/certs/                     certbot's fullchain.pem and privkey.pem, which
#                                             certbot places on the host itself
#   grafana.env                               GF_SECURITY_ADMIN_PASSWORD
set -euo pipefail

OUT="${1:-./dev-server-secrets}"
[ -e "$OUT" ] && { echo "$OUT exists; refusing to overwrite generated secrets" >&2; exit 1; }
mkdir -p "$OUT/kinotic-server/platform-secrets" "$OUT/kinotic-server/certs"

key() { openssl rand -base64 32; }
password() { openssl rand -base64 24 | tr -d '/+=' | cut -c1-24; }

cat > "$OUT/kinotic-server/platform-secrets/jwt-signing-keys" <<JSON
{"activeKeyId":"v1","keys":[{"id":"v1","key":"$(key)"}]}
JSON

cat > "$OUT/kinotic-server/secrets.yml" <<YAML
# Imported by the dev-server profile (application-dev-server.yml)
kinotic:
  domain:
    secretStorage:
      # Generated once: SecretNameDeriver derives every stored secret's name from it, so it
      # is carried to the cloud at migration
      masterKey: "$(key)"
  managementApi:
    github:
      # The shared GitHub App's private key and webhook secret, from its settings page
      appPrivateKey: |
        -----BEGIN RSA PRIVATE KEY-----
        -----END RSA PRIVATE KEY-----
      webhookSecret: ""
YAML

cat > "$OUT/kinotic-server.env" <<ENV
# terraform output -raw secrets_env, in deployment/terraform/azure/dev-server
AZURE_CLIENT_SECRET=
ENV

cat > "$OUT/grafana.env" <<ENV
GF_SECURITY_ADMIN_PASSWORD=$(password)
ENV

chmod -R go-rwx "$OUT"
echo "Written to $OUT. Fill in AZURE_CLIENT_SECRET and the GitHub App values, then: ./sync-secrets.sh $OUT"
