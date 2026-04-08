# AKS + ECK Platform

Modular Terraform + Helm for an AKS cluster running Kinotic and Elasticsearch
via the ECK operator. Single `terraform apply` deploys everything end-to-end.

Two deployment tiers controlled by `beta_mode`:
- **Beta** (default) — 1 master + 3 data ES on shared system pool (~$568/mo)
- **Production** — dedicated ES node pool, 3 master + 3 data, AKS SLA (~$2,035/mo)

See [COST.md](COST.md) for full cost breakdown and [PRODUCTION.md](PRODUCTION.md) for readiness status.

## Directory Layout

```
deployment/terraform/azure/
├── main.tf                           # Root — providers, resource group, modules
├── variables.tf                      # All input variable declarations
├── outputs.tf                        # Cluster name, endpoints, helper commands
├── helm.tf                           # Namespaces, ECK operator + stack, NetworkPolicy
├── kinotic.tf                        # Kinotic server + ES secret sync
├── tls.tf                            # Azure DNS, cert-manager, Let's Encrypt, Reloader
├── dns.tf                            # DNS A records (dynamic LB IP)
├── nodepools.tf                      # ES dedicated node pool (production only)
├── load-generator.tf                 # Conditional load generator
├── firecracker.tf                    # Conditional Firecracker VM host(s)
├── bootstrap-state.sh                # One-time: create remote state + register providers
├── terraform.tfvars                  # Environment values (template — use .auto.tfvars for secrets)
├── .gitignore                        # Ignores *.auto.tfvars, state files, PEM keys
├── config/
│   ├── kinotic-server/values.yaml    # Azure kinotic-server overrides
│   └── load-generator/values.yaml    # Azure load generator config
├── modules/
│   ├── identity/                     # Managed identities + RBAC
│   ├── networking/                   # VNet, subnets, NSGs
│   ├── aks/                          # AKS cluster (tier based on beta_mode)
│   └── firecracker/                  # VM, storage account, user_data
└── helm/
    └── eck-operator/values.yaml      # ECK operator config

Shared Helm charts (deployment/helm/):
  eck-stack/
  ├── values.yaml                     # Base (version, name, Kibana off)
  ├── values-kind.yaml                # KinD overlay
  ├── values-azure.yaml               # Production overlay (3 master + 3 data, zones, ZRS)
  └── values-azure-beta.yaml          # Beta overlay (1 master + 3 data, shared pool, ZRS)
  kinotic/                            # Kinotic server chart
  es-secret-sync/                     # ES credential copy (elastic → kinotic namespace)
  load-generator/                     # Load testing Job
```

## Prerequisites

```bash
# Azure CLI
brew install azure-cli

# Terraform
brew install terraform

# kubectl
brew install kubectl

# kubelogin (required — AKS uses Azure AD auth, no static kubeconfig)
brew install Azure/kubelogin/kubelogin
```

## First-Time Setup

### 1. Login and set subscription

```bash
az login

# If you have multiple subscriptions:
az account list --output table
az account set --subscription "<subscription-id>"
```

### 2. Push images to Docker Hub

The image tag must match `kinotic_version` in terraform.tfvars.

```bash
./gradlew :kinotic-server:bootBuildImage :kinotic-migration:bootBuildImage
docker push kinoticai/kinotic-server:<version>
docker push kinoticai/kinotic-migration:<version>
```

### 3. Bootstrap remote state and register providers

```bash
cd deployment/terraform/azure
./bootstrap-state.sh
```

This creates the Azure Storage Account for terraform state and registers all
required Azure resource providers (Storage, Compute, ContainerService, etc.).

### 4. Configure credentials

```bash
# Get your principal object ID:
az ad signed-in-user show --query id -o tsv
```

Create `local.auto.tfvars` (gitignored) with your real values:

```hcl
terraform_principal_object_id = "<paste-id-here>"
lets_encrypt_email            = "<your-email>"
```

Or update the values directly in `terraform.tfvars`.

### 5. Get DNS nameservers

Point your domain's NS records to Azure DNS **before** the full deploy.
The deploy waits for the TLS certificate, which requires DNS-01 validation.

```bash
terraform init
terraform apply -target=azurerm_dns_zone.main
terraform output dns_nameservers
```

Set these as the nameservers at your domain registrar. Wait for propagation:

```bash
dig NS kinotic.ai   # should show Azure nameservers
```

### 6. Full deploy

```bash
terraform apply
```

This creates everything end-to-end. It will block at the TLS certificate step
until cert-manager successfully completes the DNS-01 challenge, then deploys
kinotic-server with TLS.

### 7. Configure kubectl

The resource group and cluster names are derived from `project` and `environment`
in terraform.tfvars: `rg-{project}-{environment}` and `aks-{project}-{environment}`.

```bash
az aks get-credentials --resource-group rg-kinotic-production --name aks-kinotic-production
kubelogin convert-kubeconfig -l azurecli
```

### 8. Verify

```bash
kubectl get nodes
kubectl get pods -n elastic
kubectl get pods -n kinotic
curl -sk https://kinotic.ai/health/
```

## Deploy Options

```bash
# Beta (default) — 1 master + 3 data ES on shared system pool
terraform apply

# With load generator (sample data, basic auth only — run before enabling OIDC)
terraform apply -var="enable_load_generator=true"

# With Firecracker VM hosts
terraform apply -var="enable_firecracker=true"

# Scale to production topology
terraform apply -var="beta_mode=false"
```

## Deployment Order

Automatic via terraform dependency graph:

```
Resource Group
  Managed Identities + RBAC
    VNet + Subnet(s) + NSG
      AKS Cluster (system nodepool)
        [if !beta_mode] ES Node Pool (3x Standard_E8s_v5)
        Namespaces (elastic-system, elastic, kinotic, cert-manager)
          ECK Operator (elastic-system)
            Elasticsearch (elastic) — beta: 1 master + 3 data / prod: 3 master + 3 data
          ES Secret Sync Job (kinotic)
          NetworkPolicy on ES (elastic)
        Azure DNS Zone + cert-manager
          Let's Encrypt Certificate (waits for DNS propagation)
        Reloader (kube-system)
          Kinotic Server (kinotic) — 3 replicas, TLS, sticky sessions
            [if enable_load_generator] Load Generator Job (kinotic)
        DNS A records (kinotic.ai → LB IP)
      [if enable_firecracker]
        Firecracker VM(s) + Storage Account
```

## Namespaces

| Namespace | Contents |
|---|---|
| `elastic-system` | ECK operator |
| `elastic` | Elasticsearch cluster, NetworkPolicy |
| `kinotic` | Kinotic server, ES secret copy, TLS cert, load generator |
| `cert-manager` | cert-manager |
| `kube-system` | Reloader, system components |

## RBAC

| Principal | Role | Scope | Purpose |
|---|---|---|---|
| Terraform runner | AKS RBAC Cluster Admin | Resource Group | Helm/K8s providers connect to AKS |
| Control plane identity | AKS RBAC Cluster Admin | Resource Group | ECK operator API server access |
| Control plane identity | Managed Identity Operator | Kubelet identity | Assign kubelet identity to nodes |
| Kubelet identity | Network Contributor | Subnet + VNet | Manage Azure Load Balancers |
| cert-manager identity | DNS Zone Contributor | DNS Zone | Let's Encrypt DNS-01 challenges |

## Day-2 Operations

**Scale to production:** `terraform apply -var="beta_mode=false"` — adds ES node pool, ECK migrates pods to dedicated VMs, system pool gets tainted. No storage migration needed — both beta and production use Premium ZRS.

**Expand ES storage:** increase `storage:` in the eck-stack values overlay, then `terraform apply`. ECK expands PVCs online with zero downtime (one node at a time). You can only grow, never shrink.

**Upgrade ES version:** bump `version:` in `deployment/helm/eck-stack/values.yaml`, then `terraform apply`.

**Scale data nodes:** change `count:` in the eck-stack values overlay, then `terraform apply`.

**Rotate ES credentials:** re-run `helm upgrade es-secret-sync` in the kinotic namespace to copy the updated secret.

**Enable Kibana:** set `eck-kibana.enabled: true` in values, then `terraform apply`.

**Add Firecracker hosts:** `terraform apply -var="enable_firecracker=true"`.

## Authentication (Entra ID)

Terraform creates two App Registrations automatically:
- **kinotic-production-platform** — OIDC login for the kinotic application
- **kinotic-production-grafana** — OIDC login for Grafana dashboards

### Multi-tenant user isolation

Each user has a `kinoticTenantId` directory extension attribute that controls which
tenant's data they can access. After deploy:

```bash
# Get the claim name and set command
terraform output kinotic_oidc_tenant_id_claim
terraform output set_user_tenant_id
```

Assign a user to a tenant:

```bash
# Get the user's object ID
az ad user list --filter "mail eq 'nic@kinotic.ai'" --query "[0].id" -o tsv

# Use the terraform output for the exact command with the correct claim name
terraform output set_user_tenant_id
```

The claim name includes an Azure-generated prefix (`extension_{appId}_kinoticTenantId`).
Use `terraform output kinotic_oidc_tenant_id_claim` to get the exact name, then set
`tenantIdFieldName` in the kinotic-server OIDC config to match.

No Entra ID licenses are required — Azure AD Free tier handles OIDC authentication.
