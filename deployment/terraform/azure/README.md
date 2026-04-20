# Azure Deployment

Three separate terraform roots — **global** (persistent), **cluster** (disposable), **frontend** (independent).

## Directory Structure

```
deployment/terraform/azure/
├── global/                    # DNS, Entra ID — apply once, never destroy
│   ├── main.tf
│   └── terraform.tfvars
├── cluster/                   # AKS, K8s resources — destroy and rebuild freely
│   ├── main.tf
│   ├── helm.tf
│   ├── tls.tf
│   ├── dns.tf
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
├── modules/                   # Shared modules (identity, networking, aks, firecracker)
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

### 3. Deploy global resources (once)

```bash
cd global
terraform init
terraform apply
```

This creates the DNS zone and Entra ID app registrations. Copy the nameservers
to your domain registrar:

```bash
terraform output dns_nameservers
dig NS kinotic.ai  # verify propagation
```

### 4. Deploy cluster

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

### 5. Get kubectl access

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
| AKS cluster + node pools | `cluster/` | Disposable |
| VNet, subnet, identities | `cluster/` | Disposable |
| cert-manager + TLS cert | `cluster/` | Disposable (re-issued on rebuild) |
| Elasticsearch + ECK | `cluster/` | Disposable (data lost on destroy) |
| kinotic-server | `cluster/` | Disposable |
| Observability (Loki, Alloy, Grafana) | `cluster/` | Disposable |
| Firecracker VMs | `cluster/` | Disposable |
| Static Web App (SPA) | `frontend/` | Independent |
| portal.kinotic.ai CNAME | `frontend/` | Independent |

## Additional Docs

- [OPS.md](OPS.md) — Day-2 operations (scaling, upgrades, certs)
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) — Common errors and fixes
- [PRODUCTION.md](PRODUCTION.md) — Production readiness checklist
- [COST.md](COST.md) — Cost projections (~$573/mo beta, ~$2,025/mo production)
