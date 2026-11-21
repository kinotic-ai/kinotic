#!/usr/bin/env bash
# Generates what the development server needs that nothing else issues, as the secret files of the
# operator keychain (../dev-server-secret-file.sh), which sync-secrets.sh places in the host's
# secrets directory. Each server's directory is bind-mounted at /etc/kinotic in that server's
# container; the env files are merged into a container's environment by the applier on the host.
# Nothing here passes through terraform.
#
#   kinotic-servers.env                       AZURE_CLIENT_SECRET, from the Azure root, merged
#                                             into every server's environment
#   kinotic-<org|system|app>-server/secrets.yml
#                                             the secret-storage master key, the same for every
#                                             server; the org and system servers', which load the
#                                             GitHub module, also the GitHub App's private key and
#                                             webhook secret; the org server's also the Turnstile
#                                             site's secret key
#   kinotic-<org|system|app>-server/platform-secrets/jwt-signing-keys
#                                             the server's own JWT key set: an activeKeyId and
#                                             a keys list, so a key can be added at migration
#   license-key-server.env                    the license key server's Azure secrets and its
#                                             database password
#   licenses-db.env                           the same password, as the database's POSTGRES_PASSWORD
#   licenses-db-backup.env                    where kinotic-licenses-backup uploads the database's
#                                             dumps, from the Azure root
set -euo pipefail

FILES="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../dev-server-secret-file.sh"
existing="$("$FILES" list)"
[ -z "$existing" ] || { echo "The keychain holds secret files; refusing to overwrite generated secrets" >&2; exit 1; }

key() { openssl rand -base64 32; }
password() { openssl rand -base64 24 | tr -d '/+=' | cut -c1-24; }

# SecretNameDeriver derives every stored secret's name from it, and the servers store and read
# the same secrets, so there is one
master_key="$(key)"

master_key_yaml="# Imported by the dev-server profile (application-dev-server.yml)
kinotic:
  domain:
    secretStorage:
      # Generated once: SecretNameDeriver derives every stored secret's name from it, so it
      # is carried to the cloud at migration
      masterKey: \"$master_key\""
github_yaml="  managementApi:
    github:
      # The shared GitHub App's private key and webhook secret, from its settings page
      appPrivateKey: |
        -----BEGIN RSA PRIVATE KEY-----
        -----END RSA PRIVATE KEY-----
      webhookSecret: \"\"
      # Uncomment with the kinotic-internal GitHub App's private key to offer feedback
      # feedbackApp:
      #   appPrivateKey: |
      #     -----BEGIN RSA PRIVATE KEY-----
      #     -----END RSA PRIVATE KEY-----"
turnstile_yaml="  managementServer:
    turnstile:
      # The portal's Turnstile site's secret key: terraform output -raw turnstile_secret_key, in
      # deployment/terraform/azure/dev-server
      secretKey: \"\""

for server in kinotic-server-management kinotic-server-system kinotic-server-app; do
  printf '{"activeKeyId":"v1","keys":[{"id":"v1","key":"%s"}]}\n' "$(key)" \
    | "$FILES" put "$server/platform-secrets/jwt-signing-keys"
done
printf '%s\n' "$master_key_yaml" "$github_yaml" "$turnstile_yaml" | "$FILES" put kinotic-server-management/secrets.yml
printf '%s\n' "$master_key_yaml" "$github_yaml" | "$FILES" put kinotic-server-system/secrets.yml
printf '%s\n' "$master_key_yaml" | "$FILES" put kinotic-server-app/secrets.yml

"$FILES" put kinotic-servers.env <<ENV
# terraform output -raw secrets_env, in deployment/terraform/azure/dev-server
AZURE_CLIENT_SECRET=
KINOTIC_DOMAIN_PUBLISHEDSITES_CLOUDFLARE_APITOKEN=
ENV

# The license key server and its database share the database's password; the server's Azure
# half is the dev-server root's license_server_secrets_env output
db_password="$(password)"
"$FILES" put license-key-server.env <<ENV
# terraform output -raw license_server_secrets_env, in deployment/terraform/azure/dev-server
AZURE_CLIENT_SECRET=
KINOTIC_LICENSES_STAFFOIDC_CLIENTSECRET=
KINOTIC_LICENSES_DATABASE_PASSWORD=$db_password
ENV
echo "POSTGRES_PASSWORD=$db_password" | "$FILES" put licenses-db.env
"$FILES" put licenses-db-backup.env <<ENV
# terraform output -raw licenses_db_backup_env, in deployment/terraform/azure/dev-server
LICENSES_DB_BACKUP_CONTAINER_URL=
LICENSES_DB_BACKUP_SAS=
ENV

echo "Stored in the keychain. With ../dev-server-secret-file.sh edit <file>, fill in kinotic-servers.env,"
echo "license-key-server.env and licenses-db-backup.env from the dev-server root's secrets_env,"
echo "license_server_secrets_env and licenses_db_backup_env outputs, the GitHub App values in"
echo "kinotic-server-management/secrets.yml and kinotic-server-system/secrets.yml, and the dev-server root's"
echo "turnstile_secret_key output in kinotic-server-management/secrets.yml, then: ./sync-secrets.sh"
