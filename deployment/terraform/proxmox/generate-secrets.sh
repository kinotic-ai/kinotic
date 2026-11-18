#!/usr/bin/env bash
# Generates what the development server needs that nothing else issues, in the layout
# sync-secrets.sh copies to the host's secrets directory. Each server's directory is
# bind-mounted at /etc/kinotic in that server's container; the env files are merged into a
# container's environment by the applier on the host. Nothing here passes through terraform.
#
#   kinotic-servers.env                       AZURE_CLIENT_SECRET, from the Azure root, merged
#                                             into every server's environment
#   kinotic-<org|system|app>-server/secrets.yml
#                                             the secret-storage master key, the same for every
#                                             server; the org and system servers', which load the
#                                             GitHub module, also the GitHub App's private key and
#                                             webhook secret
#   kinotic-<org|system|app>-server/platform-secrets/jwt-signing-keys
#                                             the server's own JWT key set: an activeKeyId and
#                                             a keys list, so a key can be added at migration
#   kinotic-<org|system|app>-server/certs/    certbot's fullchain.pem and privkey.pem for the
#                                             server's names, which certbot places on the host itself
#   license-key-server.env                    the license key server's Azure secrets and its
#                                             database password
#   licenses-db.env                           the same password, as the database's POSTGRES_PASSWORD
#   license-key-server/certs/, edge/          certbot's certificate for the license key server's
#                                             names, and the same as the PEM the edge terminates with
set -euo pipefail
umask 077

OUT="${1:-./dev-server-secrets}"
[ -e "$OUT" ] && { echo "$OUT exists; refusing to overwrite generated secrets" >&2; exit 1; }

key() { openssl rand -base64 32; }
password() { openssl rand -base64 24 | tr -d '/+=' | cut -c1-24; }

# SecretNameDeriver derives every stored secret's name from it, and the servers store and read
# the same secrets, so there is one
master_key="$(key)"

for server in kinotic-server-management kinotic-server-system kinotic-server-app; do
  mkdir -p "$OUT/$server/platform-secrets" "$OUT/$server/certs"

  cat > "$OUT/$server/platform-secrets/jwt-signing-keys" <<JSON
{"activeKeyId":"v1","keys":[{"id":"v1","key":"$(key)"}]}
JSON

  cat > "$OUT/$server/secrets.yml" <<YAML
# Imported by the dev-server profile (application-dev-server.yml)
kinotic:
  domain:
    secretStorage:
      # Generated once: SecretNameDeriver derives every stored secret's name from it, so it
      # is carried to the cloud at migration
      masterKey: "$master_key"
YAML
done

for server in kinotic-server-management kinotic-server-system; do
  cat >> "$OUT/$server/secrets.yml" <<YAML
  managementApi:
    github:
      # The shared GitHub App's private key and webhook secret, from its settings page
      appPrivateKey: |
        -----BEGIN RSA PRIVATE KEY-----
        -----END RSA PRIVATE KEY-----
      webhookSecret: ""
YAML
done

cat > "$OUT/kinotic-servers.env" <<ENV
# terraform output -raw secrets_env, in deployment/terraform/azure/dev-server
AZURE_CLIENT_SECRET=
KINOTIC_DOMAIN_PUBLISHEDSITES_CLOUDFLARE_APITOKEN=
ENV

# The license key server and its database share the database's password; the server's Azure
# half is the dev-server root's license_server_secrets_env output
db_password="$(password)"
mkdir -p "$OUT/license-key-server/certs" "$OUT/edge"
cat > "$OUT/license-key-server.env" <<ENV
# terraform output -raw license_server_secrets_env, in deployment/terraform/azure/dev-server
AZURE_CLIENT_SECRET=
KINOTIC_LICENSES_STAFFOIDC_CLIENTSECRET=
KINOTIC_LICENSES_DATABASE_PASSWORD=$db_password
ENV
echo "POSTGRES_PASSWORD=$db_password" > "$OUT/licenses-db.env"

chmod -R go-rwx "$OUT"
echo "Written to $OUT. Fill in kinotic-servers.env and license-key-server.env from the dev-server root's secrets_env and"
echo "license_server_secrets_env outputs, and the GitHub App values"
echo "in kinotic-server-management/secrets.yml and kinotic-server-system/secrets.yml,"
echo "then: ./sync-secrets.sh $OUT"
