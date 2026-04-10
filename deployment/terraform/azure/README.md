# Azure Deployment — Deploy Day Guide

First-time setup and deployment of the Kinotic platform on Azure AKS.

For day-2 operations (scaling, upgrades, troubleshooting), see [OPS.md](OPS.md).
For production readiness checklist, see [PRODUCTION.md](PRODUCTION.md).
For cost projections, see [COST.md](COST.md).

## Prerequisites

```bash
brew install azure-cli terraform kubectl helm jq
brew install Azure/kubelogin/kubelogin
```

## Step 1: Login

```bash
az login
az account set --subscription "<subscription-id>"
```

## Step 2: Bootstrap State Storage

```bash
cd deployment/terraform/azure
./bootstrap-state.sh
```

This registers Azure resource providers and creates the storage account for terraform state.

## Step 3: Configure

```bash
# Get your principal object ID
az ad signed-in-user show --query id -o tsv
```

Create `local.auto.tfvars` (gitignored) with your credentials:

```hcl
terraform_principal_object_id = "<paste-id-here>"
lets_encrypt_email            = "<your-email>"
```

Review `terraform.tfvars` for environment settings (region, node count, K8s version).

## Step 4: DNS Zone

Create the DNS zone first to get nameservers for your registrar:

```bash
terraform init
terraform apply -target=azurerm_dns_zone.main -target=azurerm_resource_group.global
terraform output dns_nameservers
```

Update your domain registrar's NS records. Verify propagation:

```bash
dig NS kinotic.ai
```

## Step 5: AKS Infrastructure

Create AKS and networking (helm/kubernetes providers need a cluster to connect):

```bash
terraform apply \
  -target=azurerm_resource_group.main \
  -target=module.identity \
  -target=module.networking \
  -target=module.aks
```

This takes ~10 minutes. Once complete, get kubectl access:

```bash
az aks get-credentials --resource-group rg-kinotic-production --name aks-kinotic-production
kubelogin convert-kubeconfig -l azurecli
kubectl get nodes
```

## Step 6: Full Deploy

```bash
terraform apply
```

This deploys everything remaining: cert-manager, Let's Encrypt TLS, ECK, Elasticsearch,
kinotic-server, observability stack (Loki + Alloy + Grafana), Entra ID app registrations.

The deploy blocks at the TLS certificate step until cert-manager completes the DNS-01
challenge (~2-5 minutes after DNS propagation).

## Step 7: Verify

```bash
kubectl get pods -n elastic
kubectl get pods -n kinotic
kubectl get pods -n observability
curl -sk https://kinotic.ai/health/
```

## Deploy Options

```bash
# Beta (default) — shared system pool, reduced ES sizing
terraform apply

# With load generator
terraform apply -var="enable_load_generator=true"

# With Firecracker VM hosts
terraform apply -var="enable_firecracker=true"

# Disable Grafana Entra ID (local admin/admin instead)
terraform apply -var="disable_grafana_entra=true"

# Scale to production topology
terraform apply -var="beta_mode=false"
```

## What Gets Created

```
rg-kinotic-global (region-independent)
  └── Azure DNS Zone (kinotic.ai)

rg-kinotic-production (centralus)
  ├── Managed Identities + RBAC
  ├── VNet + Subnet + NSG
  ├── AKS Cluster (3x Standard_D4s_v5)
  │   ├── Namespace: elastic-system
  │   │   └── ECK Operator
  │   ├── Namespace: elastic
  │   │   ├── Elasticsearch (1 master + 3 data)
  │   │   └── NetworkPolicy (kinotic + elastic-system only)
  │   ├── Namespace: kinotic
  │   │   ├── kinotic-server (2 replicas, TLS)
  │   │   └── ES Secret Sync Job
  │   ├── Namespace: cert-manager
  │   │   └── cert-manager + Let's Encrypt ClusterIssuer
  │   ├── Namespace: observability
  │   │   ├── Loki (log storage)
  │   │   ├── Alloy (log collector, DaemonSet)
  │   │   └── Grafana (dashboards, Entra ID auth)
  │   └── kube-system
  │       └── Reloader (cert rotation)
  ├── Entra ID App Registration (kinotic-platform)
  └── Entra ID App Registration (grafana)
```

## Namespaces

| Namespace | Contents |
|---|---|
| `elastic-system` | ECK operator |
| `elastic` | Elasticsearch cluster, NetworkPolicy |
| `kinotic` | kinotic-server, ES secret copy, TLS cert |
| `cert-manager` | cert-manager |
| `observability` | Loki, Alloy, Grafana |
| `kube-system` | Reloader, Cilium, system components |

## Networking

AKS uses **Azure CNI Overlay** with **Cilium**:
- Pods get IPs from overlay CIDR, not the VNet
- Cilium enforces NetworkPolicy (replaces Calico)
- ES restricted to traffic from `kinotic` and `elastic-system` namespaces

## RBAC

| Principal | Role | Scope |
|---|---|---|
| Terraform runner | AKS RBAC Cluster Admin | Resource Group |
| Control plane identity | AKS RBAC Cluster Admin | Resource Group |
| Control plane identity | Managed Identity Operator | Kubelet identity |
| Kubelet identity | Network Contributor | Subnet + VNet |
| cert-manager identity | DNS Zone Contributor | DNS Zone |

## Directory Layout

```
deployment/terraform/azure/
├── main.tf                        # Providers, resource groups, modules
├── helm.tf                        # Namespaces, ECK operator + stack, NetworkPolicy
├── kinotic.tf                     # kinotic-server + ES secret sync
├── tls.tf                         # cert-manager, Let's Encrypt, Reloader
├── dns.tf                         # DNS A records (dynamic LB IP)
├── observability.tf               # Loki + Alloy + Grafana + Entra ID
├── auth.tf                        # Kinotic platform OIDC App Registration
├── nodepools.tf                   # ES dedicated node pool (production only)
├── firecracker.tf                 # Conditional Firecracker VMs
├── load-generator.tf              # Conditional load generator
├── bootstrap-state.sh             # One-time state storage + provider registration
├── terraform.tfvars               # Environment config (template)
├── .gitignore                     # Ignores *.auto.tfvars, state, PEM keys
├── config/
│   ├── kinotic-server/values.yaml
│   └── load-generator/values.yaml
├── modules/
│   ├── identity/
│   ├── networking/
│   ├── aks/
│   └── firecracker/
└── helm/
    └── eck-operator/values.yaml
```
