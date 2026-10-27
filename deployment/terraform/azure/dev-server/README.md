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
| Cloudflare records and Worker routes | `dev-portal.kinotic.ai`, `dev-console.kinotic.ai`, and `dev-summit42.kinotic.ai` when `summit42_enabled` | The portal, the system console and Summit 42's UI, served by the Worker from `sites/<hostname>/` in the sites account, on the platform zone and never the customers' (`modules/cloudflare-sites`, as the cluster's portal and console) |
| Cloudflare records | `apps-api.dev.kinotic.io`, `*.apps-api.dev.kinotic.io` | The app server and every application's API host `<organizationId>--<applicationId>.apps-api.dev.kinotic.io`, proxied to the router's public address |
| Key vault | `kv-kinotic-dev` | The servers' secret storage (`kinotic.domain.secretStorage.backend: AZURE`) |
| Storage account + container | `stkinoticdevsnapshots` / `elasticsearch-snapshots` | The nightly Elasticsearch snapshot repository, and the migration vehicle |
| Cloudflare records | `dev-api.kinotic.ai`, `dev-system-api.kinotic.ai`, and `dev-summit42-api.kinotic.ai` when `summit42_enabled` | The router's public address, unproxied, for the management server, the system server and the Summit 42 server; `kinotic-dyndns.timer` on the host keeps them current with the DNS token |
| Service principal | `kinotic-dev-server` | The identity the servers run as: Storage Blob Data Contributor on the sites account, Contributor on the email service, Key Vault Secrets Officer on `kv-kinotic-dev` |
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
terraform output -raw secrets_env       # → kinotic-servers.env in the secrets directory, placed by sync-secrets.sh
```

The social sign-in providers the migration seeds (the `ORG_SIGNUP` rows of `kinotic_oidc_configuration`)
resolve their client secrets from `kv-kinotic-dev` by name, so each provider's secret goes
there once, and its registration lists the management server's callback URLs under `dev-api`:

```bash
az keyvault secret set --vault-name kv-kinotic-dev --name github-platform --value "$(cat)" >/dev/null   # the App's client secret on stdin
```

`terraform destroy` removes the resource group with everything in it, the records in both
zones, the service principal, and its role assignments. The snapshot account goes with the group, so
copy the last snapshot elsewhere first if it still matters.
