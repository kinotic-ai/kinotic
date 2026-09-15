# The development server's Azure side

Everything the shared development server needs from Azure, applied once from an operator's
machine. It reuses `modules/dev-environment`, the same module a developer's `dev/` root uses,
and adds what a server peers depend on needs: a key vault for the server's secret storage, a
storage account for Elasticsearch snapshots, the portal and the system console on the same
Front Door as the published sites, and a hostname for the API with the rights to issue its
certificate.

| Resource | Name | Purpose |
|---|---|---|
| Resource group | `rg-kinotic-dev` | Holds everything below but the service principal |
| Front Door Standard profile + endpoint | `afd-kinotic-dev-sites` | Serves every published UI at `<label>.apps-dev.kinotic.ai` (`modules/sites`) |
| Sites storage account | `stkinoticdevsites` | Every site's files under `sites/<hostname>/` |
| Key vault | `kv-kinotic-dev-sites` | The Let's Encrypt wildcard certificate Front Door reads |
| Key vault | `kv-kinotic-dev` | The server's secret storage (`kinotic.domain.secretStorage.backend: AZURE`) |
| Storage account + container | `stkinoticdevsnapshots` / `elasticsearch-snapshots` | The nightly Elasticsearch snapshot repository, and the migration vehicle |
| Front Door domains + CNAMEs | `dev-portal.kinotic.ai`, `dev-console.kinotic.ai` | The portal and the system console, served from `sites/<hostname>/` in the sites account on managed certificates |
| DNS A record | `dev-api.kinotic.ai` | The router's public address; `kinotic-dyndns.timer` on the host keeps it current |
| Service principal | `kinotic-dev-server` | The identity the server runs as: Storage Blob Data Contributor on the sites account, Contributor on the email service, Key Vault Secrets Officer on `kv-kinotic-dev`, DNS Zone Contributor on the zone for certbot and the address updater |
| Role assignment | the operator | Storage Blob Data Contributor on the sites account, for `deploy-ui.sh` |

## Applying

```bash
az login
export ARM_SUBSCRIPTION_ID=$(az account show --query id -o tsv)
cd deployment/terraform/azure/dev-server
```

```hcl
# local.auto.tfvars (gitignored)
public_ip          = "203.0.113.10"     # the router's public address today; the host keeps the record current
lets_encrypt_email = "you@example.com"
```

```bash
terraform init
terraform apply -target=module.environment.module.sites.azurerm_cdn_frontdoor_profile.sites   # the profile first: its identity's principal id is unknown until it exists
terraform apply
```

Then the two UIs, built for this server and uploaded into the sites account, where Front
Door serves each from `sites/<hostname>/` exactly as it serves a published site. Again
whenever `kinotic-frontend` changes:

```bash
./deploy-ui.sh                # pnpm build --mode dev-server for apps/portal and apps/system, then upload
```

The proxmox root reads this root's state file directly (`../azure/dev-server/terraform.tfstate`),
so both are applied from the same checkout. Two outputs feed kinotic-server:

```bash
terraform output dev_server_env         # merged into the server's environment by the proxmox root
terraform output -raw secrets_env       # → kinotic-server.env in the secrets directory, placed by sync-secrets.sh
```

`terraform destroy` removes the resource group with everything in it, the DNS record, the
service principal, and its role assignments. The snapshot account goes with the group, so
copy the last snapshot elsewhere first if it still matters.
