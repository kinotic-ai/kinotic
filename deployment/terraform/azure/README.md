# Azure Deployment

Three separate terraform roots — **global** (persistent), **cluster** (disposable), **frontend** (independent) — plus **dev**, a developer's own UI publishing resources.

## Directory Structure

```
deployment/terraform/azure/
├── global/                    # DNS, Entra ID, Key Vault, email — apply once, never destroy
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
├── frontend/                  # Static Web App for SPA — deploy independently
│   ├── main.tf
│   ├── deploy.sh
│   └── terraform.tfvars
├── dev/                       # Storage resource group + Front Door for a kinotic-server on a developer machine
│   ├── main.tf
│   └── terraform.tfvars
├── modules/                   # Shared modules (aks, firecracker, identity, micro-vm-node, networking)
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
TF_VAR_google_client_secret=... TF_VAR_github_client_secret=... terraform apply
```

This creates the DNS zone and Entra ID app registrations. Copy the nameservers
to your domain registrar:

```bash
terraform output dns_nameservers
dig NS kinotic.ai  # verify propagation
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

Global resources (DNS zone, Entra ID apps) are untouched. The cluster reads
from global state via `terraform_remote_state`. No imports, no state surgery.

## Deploy Frontend (SPA)

```bash
cd frontend
terraform init
terraform apply     # creates Static Web App + DNS CNAME (first time only)
./deploy.sh         # build + deploy SPA (run after every frontend change)
```

## Developer UI Publishing

A kinotic-server on a developer machine publishes UIs to a real subscription with the `dev`
root: a resource group the organization storage accounts are created in, a Front Door
Standard profile and endpoint under `apps-<environment>.<zone>`, and a service principal
for the server holding Contributor and Storage Blob Data Contributor on the group, DNS Zone
Contributor on the zone, and Contributor on the email service. State is local, one
environment per developer.

The principal's `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET` and `AZURE_TENANT_ID` are written to
`.env.local` at the repository root (gitignored), replacing those three lines when present
and leaving the rest of the file alone; the server picks them up through
`DefaultAzureCredential`. Its secret is also in this root's local state.

```bash
cd dev
terraform init
terraform apply   # environment = "local" in terraform.tfvars; pick a name of your own
terraform output -raw application_local_yml > ../../../../kinotic-server/src/main/resources/application-local.yml
```

Then run the server with `SPRING_PROFILES_ACTIVE=development,local`. The contributing guide
on the website (Testing → Publishing UIs against Azure) has the full walkthrough.

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
| DNS zone (kinotic.ai) | `global/` | Permanent |
| Entra ID App Registrations | `global/` | Permanent |
| Azure Communication Services (email) | `global/` | Permanent |
| Key Vault (platform secrets) | `global/` | Permanent |
| AKS cluster + node pools | `cluster/` | Disposable |
| VNet, subnet, identities | `cluster/` | Disposable |
| cert-manager + TLS cert | `cluster/` | Disposable (re-issued on rebuild) |
| Elasticsearch + ECK | `cluster/` | Disposable (data lost on destroy) |
| kinotic-server | `cluster/` | Disposable |
| Observability (Loki, Alloy, Grafana) | `cluster/` | Disposable |
| Firecracker VMs | `cluster/` | Disposable |
| Static Web App (SPA) | `frontend/` | Independent |
| portal.kinotic.ai CNAME | `frontend/` | Independent |
| Developer storage resource group + Front Door (apps-<environment>.kinotic.ai) | `dev/` | Per developer, local state |

## Additional Docs

- [OPS.md](OPS.md) — Day-2 operations (scaling, upgrades, certs)
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) — Common errors and fixes
- [PRODUCTION.md](PRODUCTION.md) — Production readiness checklist
- [COST.md](COST.md) — Cost projections (~$573/mo beta, ~$2,025/mo production)
