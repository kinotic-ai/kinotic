# AKS + ECK Platform

Modular Terraform + Helm for an AKS cluster running Kinotic and Elasticsearch
via the ECK operator. Single `terraform apply` deploys everything end-to-end.

Two deployment tiers controlled by `beta_mode`:
- **Beta** (default) — all workloads on shared system pool, single ES node (~$511/mo)
- **Production** — dedicated ES node pool, 3 master + 3 data ES nodes, AKS SLA (~$2,035/mo)

See [COST.md](COST.md) for full cost breakdown and [PRODUCTION.md](PRODUCTION.md) for readiness status.

## Directory Layout

```
deployment/terraform/azure/
├── main.tf                           # Root — providers, resource group, modules
├── variables.tf                      # All input variable declarations
├── outputs.tf                        # Cluster name, endpoints, helper commands
├── helm.tf                           # Helm/K8s providers, namespaces, ECK, NetworkPolicy
├── kinotic.tf                        # Kinotic server + ES secret sync
├── tls.tf                            # Azure DNS, cert-manager, Let's Encrypt, Reloader
├── dns.tf                            # DNS A records (dynamic LB IP)
├── nodepools.tf                      # ES dedicated node pool (production only)
├── load-generator.tf                 # Conditional load generator
├── firecracker.tf                    # Conditional Firecracker VM host(s)
├── bootstrap-state.sh                # One-time: create remote state storage
├── terraform.tfvars                  # Environment variable values
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
  ├── values-azure.yaml               # Production overlay (master+data, zones, ZRS)
  └── values-azure-beta.yaml          # Beta overlay (single node, shared pool)
  kinotic/                            # Kinotic server chart
  es-secret-sync/                     # ES credential copy (elastic → kinotic namespace)
  load-generator/                     # Load testing Job
```

## First-Time Setup

### 1. Bootstrap remote state

```bash
az login
cd deployment/terraform/azure
./bootstrap-state.sh
```

### 2. Get your Terraform principal object ID

```bash
az ad signed-in-user show --query id -o tsv
```

### 3. Update terraform.tfvars

```hcl
terraform_principal_object_id = "<paste-id-here>"
lets_encrypt_email            = "<your-email>"
```

### 4. Deploy

```bash
terraform init
terraform plan
terraform apply
```

### 5. Update DNS nameservers

After the first apply, point your domain's NS records to Azure:

```bash
terraform output dns_nameservers
```

Set these as the nameservers at your domain registrar. cert-manager will issue
the Let's Encrypt certificate once DNS propagates.

## Deploy Options

```bash
# Beta (default) — single ES node, shared system pool
terraform apply

# With load generator (sample data)
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
            Elasticsearch (elastic) — beta: 1 node / prod: 3 master + 3 data
          ES Secret Sync Job (kinotic)
          NetworkPolicy on ES (elastic)
        Azure DNS Zone + cert-manager + Let's Encrypt Certificate
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

## Verify After Deploy

```bash
az aks get-credentials --resource-group rg-kinotic-dev --name aks-kinotic-dev

kubectl get nodes -o wide
kubectl get elasticsearch -n elastic
kubectl get pods -n elastic
kubectl get pods -n kinotic

# Health check
curl -sk https://kinotic.ai/health/

# Get elastic password
kubectl get secret kinotic-es-es-elastic-user \
  -n elastic -o jsonpath='{.data.elastic}' | base64 -d && echo
```

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

**Expand ES storage:** increase `storage:` in the eck-stack values overlay, then `terraform apply`. ECK expands PVCs online with zero downtime (one node at a time). Premium ZRS supports online expansion. You can only grow, never shrink.

**Upgrade ES version:** bump `version:` in `deployment/helm/eck-stack/values.yaml`, then `terraform apply`.

**Scale data nodes:** change `count:` in the eck-stack values overlay, then `terraform apply`.

**Rotate ES credentials:** re-run `helm upgrade es-secret-sync` in the kinotic namespace to copy the updated secret.

**Enable Kibana:** set `eck-kibana.enabled: true` in values, then `terraform apply`.

**Add Firecracker hosts:** `terraform apply -var="enable_firecracker=true"`.
