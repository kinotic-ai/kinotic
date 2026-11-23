# The development server's Azure side

Everything the shared development server needs from Azure and Cloudflare, applied once from an
operator's machine. It reuses `modules/dev-environment`, the same module a developer's `dev/`
root uses, and adds what a server peers depend on needs: a key vault for the servers' secret
storage, a storage account for Elasticsearch snapshots, the portal and the system console on
the sites Worker at hostnames of the platform zone, and a hostname for each server's API.

| Resource | Name | Purpose |
|---|---|---|
| Resource group | `rg-kinotic-dev` | Holds everything below but the service principal |
| Sites storage account | `stkinoticdevsites` | Every site's files under `sites/<hostname>/`, private; the Worker reads with the SAS this root mints (`modules/sites`) |
| Cloudflare sites Worker and routes | `kinotic-dev-sites` | Serves every published UI at `<app>--<ui>.<org>.dev.kinotic.io` from the account, with the zone's settings and Total TLS (`modules/cloudflare-sites`) |
| Cloudflare records and Worker routes | `portal.kinotic.ai`, `console.kinotic.ai`, and `dev-summit42.kinotic.ai` when `summit42_enabled` | The portal, the system console and Summit 42's UI, served by the Worker from `sites/<hostname>/` in the sites account, on the platform zone and never the customers' (`modules/cloudflare-sites`, as the cluster's portal and console) |
| Cloudflare records | `apps-api.dev.kinotic.io`, `*.apps-api.dev.kinotic.io` | The app server and every application's API host `<organizationId>--<applicationId>.apps-api.dev.kinotic.io`, proxied to the router's public address |
| Cloudflare Turnstile site | `kinotic-dev portal` | The challenge the portal's find-my-organizations form presents at `portal.kinotic.ai`; the management server takes its site key from the proxmox root and its secret key from `secrets.yml` (`turnstile_secret_key`) |
| Key vault | `kv-kinotic-dev` | The servers' secret storage (`kinotic.domain.secretStorage.backend: AZURE`) |
| Storage account + container | `stkinoticdevsnapshots` / `elasticsearch-snapshots` | The nightly Elasticsearch snapshot repository, and the migration vehicle |
| Cloudflare records | `api.kinotic.ai`, `system-api.kinotic.ai`, and `dev-summit42-api.kinotic.ai` when `summit42_enabled` | The router's public address, unproxied, for the management server, the system server and the Summit 42 server; `kinotic-dyndns.timer` on the host keeps them current with the DNS token |
| Service principal | `kinotic-dev-server` | The identity the servers run as: Storage Blob Data Contributor on the sites account, Contributor on the email service, Key Vault Secrets Officer on `kv-kinotic-dev` |
| Service principal | `kinotic-dev-license-server` | The identity the license key server runs as: Key Vault Crypto User on `kinotic-license-signing` in `kv-kinotic-platform` alone, which signs license keys, and Contributor on the email service (`license-server.tf`) |
| App registration | `Kinotic, Inc` (`azuread_application.license_console`) | Staff sign-in to the license key server's console, single-tenant, returning to `https://licenses-console.kinotic.ai/api/auth/staff/callback` |
| Cloudflare records | `licenses.kinotic.ai`, `licenses-console.kinotic.ai` | The router's public address, unproxied, for the license key server's two sites; kept current like the servers' |
| Role assignment | the operator | Storage Blob Data Contributor on the sites account, for `deploy-ui.sh`; Key Vault Secrets Officer on `kv-kinotic-dev`, to place the social sign-in client secrets |

## Applying

```bash
az login
export ARM_SUBSCRIPTION_ID=$(az account show --query id -o tsv)
cd deployment/terraform/azure/dev-server
```

```hcl
# local.auto.tfvars (gitignored); the Cloudflare tokens are in the operator keychain
# (../../proxmox/README.md, "Operator secrets"), never in this file
public_ip             = "203.0.113.10"     # the router's public address today; the host keeps the record current
lets_encrypt_email    = "you@example.com"
cloudflare_account_id = "..."
cloudflare_zone_id    = "..."              # the customers' zone
```

```bash
terraform init
../../with-dev-server-secrets.sh terraform apply
```

Then the two UIs, built for this server and uploaded into the sites account, where the sites
Worker serves each from `sites/<hostname>/` exactly as it serves a published site. Again
whenever `kinotic-frontend` changes:

```bash
../deploy-ui.sh dev-server dev-server     # pnpm build --mode dev-server for apps/portal and apps/system, then upload
```

The proxmox root reads this root's state file directly (`../azure/dev-server/terraform.tfstate`),
so both are applied from the same checkout. Two outputs feed the servers:

```bash
terraform output dev_server_env         # merged into every server's environment by the proxmox root
terraform output -raw secrets_env       # → the keychain's kinotic-servers.env (dev-server-secret-file.sh), placed by sync-secrets.sh
```

The license key server takes two of its own: `license_server_env`, its environment in the proxmox root, and
`license_server_secrets_env`, which goes into `license-key-server.env` beside the database password
(`../../proxmox/README.md`, "The license key server").

The social sign-in providers the migration seeds (the `ORG_SIGNUP` rows of `kinotic_oidc_configuration`)
resolve their client secrets from `kv-kinotic-dev` by name, so each provider's secret goes
there once, and its registration lists the management server's callback URLs under `api`:

```bash
az keyvault secret set --vault-name kv-kinotic-dev --name github-platform --value "$(cat)" >/dev/null   # the App's client secret on stdin
```

System users sign in to the system console through a single-tenant registration, shown as `Kinotic, Inc` at sign-in
(`azuread_application.system_console` in `system-console.tf`), which a system user adds in the console under **Sign-in providers** as a Microsoft Entra ID
provider:

```bash
terraform output -raw system_sign_in_directory_id    # the provider's account
terraform output -raw system_sign_in_client_id
terraform output -raw system_sign_in_client_secret
```

The console then shows the provider's callback URL. Its last segment is the provider's id, which goes in
`system_sign_in_provider_id` (`local.auto.tfvars`) for the next apply to list that URL on the registration.

`terraform destroy` removes the resource group with everything in it, the records in both
zones, the service principal, and its role assignments. The snapshot account goes with the group, so
copy the last snapshot elsewhere first if it still matters.
