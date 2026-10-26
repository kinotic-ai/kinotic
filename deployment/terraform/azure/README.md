# Azure Deployment

Two terraform roots for the cloud — **global** (persistent) and **cluster** (disposable) — plus **dev-server**, the shared development server's Azure side.

## Directory Structure

```
deployment/terraform/azure/
├── global/                    # The platform zone on Cloudflare, Entra ID, Key Vault, email — apply once, never destroy
│   ├── main.tf
│   ├── email.tf
│   ├── keyvault.tf
│   └── terraform.tfvars
├── cluster/                   # AKS, K8s resources — destroy and rebuild freely
│   ├── main.tf
│   ├── helm.tf
│   ├── tls.tf
│   ├── dns.tf
│   ├── email.tf
│   ├── keyvault.tf
│   ├── kinotic.tf
│   ├── observability.tf
│   ├── nodepools.tf
│   ├── firecracker.tf
│   ├── load-generator.tf
│   ├── variables.tf
│   ├── outputs.tf
│   ├── terraform.tfvars
│   ├── config/
│   └── helm/
├── dev-server/                # The shared development server's Azure side: the module plus a server key vault, snapshots, and a hostname
│   ├── README.md
│   ├── main.tf
│   ├── outputs.tf
│   └── variables.tf
├── modules/                   # Shared modules (aks, cloudflare-sites, dev-environment, firecracker, identity, micro-vm-node, networking, sites)
├── deploy-ui.sh               # Builds the portal and the system console and uploads them into a root's sites account
├── bootstrap-state.sh         # One-time state storage setup
├── OPS.md                     # Day-2 operations
├── TROUBLESHOOTING.md         # Common errors and fixes
├── PRODUCTION.md              # Production readiness checklist
└── COST.md                    # Cost projections
```

## First-Time Setup

### 1. Prerequisites

```bash
brew install azure-cli terraform kubectl helm jq
brew install Azure/kubelogin/kubelogin
az login
```

### 2. Bootstrap state storage

```bash
cd deployment/terraform/azure
./bootstrap-state.sh
```

### 3. Create the terraform operators group (once)

The platform Key Vault uses RBAC, and `global/keyvault.tf` grants the
`Key Vault Secrets Officer` data-plane role to the `kinotic-terraform-operators`
Entra group. Anyone who runs `terraform apply` in `global/` must be a member.

```bash
GROUP_ID=$(az ad group create --display-name kinotic-terraform-operators \
  --mail-nickname kinotic-terraform-operators --query id -o tsv)
az ad group member add --group "$GROUP_ID" \
  --member-id "$(az ad signed-in-user show --query id -o tsv)"
```

To add another operator later:

```bash
az ad group member add --group kinotic-terraform-operators \
  --member-id "$(az ad user show --id <upn> --query id -o tsv)"
```

### 4. Deploy global resources (once)

```bash
cd global
terraform init
# cloudflare_account_id and cloudflare_api_token in local.auto.tfvars (gitignored)
TF_VAR_google_client_secret=... TF_VAR_github_client_secret=... TF_VAR_cloudflare_dns_api_token=... \
  TF_VAR_github_app_private_key="$(cat <the App's private-key .pem>)" TF_VAR_github_webhook_secret=... \
  terraform apply
```

This creates the platform zone on Cloudflare with the email records, the Entra ID app
registrations, and the platform Key Vault holding the secrets the servers mount. Point the
domain at Cloudflare's nameservers at your registrar:

```bash
terraform output dns_nameservers
dig NS kinotic.ai  # verify propagation
```

A domain moving from Azure DNS keeps its Azure zone answering meanwhile: the roots forget
their Azure records and the zone rather than destroying them (`removed` blocks), so the two
zones answer alike until the change has propagated. Then delete the Azure zone by hand:

```bash
az network dns zone delete --name kinotic.ai --resource-group rg-kinotic-global
```

### 5. Deploy cluster

```bash
cd ../cluster

# Get your principal object ID
az ad signed-in-user show --query id -o tsv
```

Create `local.auto.tfvars` (gitignored):

```hcl
terraform_principal_object_id = "<paste-id>"
lets_encrypt_email            = "<your-email>"
```

```bash
terraform init
terraform apply
```

### 6. Get kubectl access

```bash
az aks get-credentials --resource-group rg-kinotic-production --name aks-kinotic-production
kubelogin convert-kubeconfig -l azurecli
kubectl get nodes
```

## Teardown and Rebuild

```bash
# Destroy cluster (takes ~10 min)
cd cluster
terraform destroy

# Rebuild
terraform apply
```

Global resources (the platform zone, Entra ID apps) are untouched. The cluster reads
from global state via `terraform_remote_state`. No imports, no state surgery.

## Deploy the portal and the system console

The cluster's own UIs are served by the sites Worker from the cluster's sites account at
`portal.kinotic.ai` and `console.kinotic.ai`, the way every published site is served, from
`sites/<hostname>/`; the cluster root creates their records and routes and grants the operator
the upload role.

```bash
./deploy-ui.sh cluster azure     # pnpm build --mode azure for apps/portal and apps/system, then upload; after every frontend change
```

## Deploy Options

```bash
cd cluster

# Beta (default)
terraform apply

# With load generator
terraform apply -var="enable_load_generator=true"

# With Firecracker VMs
terraform apply -var="enable_firecracker=true"

# Scale to production
terraform apply -var="beta_mode=false"
```

## What's Where

| Resource | Terraform | Lifecycle |
|---|---|---|
| The platform zone (kinotic.ai) on Cloudflare, with the email records | `global/` | Permanent |
| Entra ID App Registrations | `global/` | Permanent |
| Azure Communication Services (email) | `global/` | Permanent |
| Key Vault (system secrets) | `global/` | Permanent |
| AKS cluster + node pools | `cluster/` | Disposable |
| VNet, subnet, identities | `cluster/` | Disposable |
| cert-manager + TLS cert | `cluster/` | Disposable (re-issued on rebuild) |
| Elasticsearch + ECK | `cluster/` | Disposable (data lost on destroy) |
| The Kinotic servers (org, system, app) | `cluster/` | Disposable |
| Observability (Loki, Tempo, Mimir, Alloy, Grafana) | `cluster/` | Disposable |
| Firecracker VMs | `cluster/` | Disposable |
| Sites account, Cloudflare sites Worker (kinotic.io), portal.kinotic.ai and console.kinotic.ai | `cluster/` | Disposable; `deploy-ui.sh cluster azure` uploads the UIs again |
| Development server: sites account, Cloudflare sites Worker (dev.kinotic.io), server key vault, snapshot storage | `dev-server/` | One per zone, local state |

## Additional Docs

- [OPS.md](OPS.md) — Day-2 operations (scaling, upgrades, certs)
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) — Common errors and fixes
- [PRODUCTION.md](PRODUCTION.md) — Production readiness checklist
- [COST.md](COST.md) — Cost projections (~$573/mo beta, ~$2,025/mo production)
