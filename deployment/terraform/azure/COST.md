# Azure Cost Projection

Estimated monthly costs for West US 2. Prices are approximate (pay-as-you-go).

## Azure Resource Inventory

### Always Created (Beta + Production)

| Resource | Azure Service | Cost |
|---|---|---|
| Resource Group | Azure Resource Manager | Free |
| VNet + Subnets | Virtual Network | Free |
| Network Security Groups (x2) | NSG | Free |
| Managed Identities (x3) | Managed Identity (control plane, kubelet, cert-manager) | Free |
| Role Assignments (x5) | Azure RBAC | Free |
| Federated Identity Credential | Workload Identity | Free |
| AKS Cluster | Azure Kubernetes Service (Free in beta, Standard in prod) | Free / $73 |
| System Node Pool (3x VMs) | Virtual Machines | **Paid** |
| System OS Disks | Managed Disks | **Paid** |
| Azure DNS Zone | Azure DNS (kinotic.ai) | **~$0.50/mo** |
| DNS A Records (root + grafana) | Azure DNS | Included in zone |
| DNS Queries | Azure DNS | **~$0.40/mo per 1M queries** |
| Load Balancer (kinotic-server) | Standard LB + Public IP | **~$22/mo** |
| Load Balancer (Grafana) | Standard LB + Public IP | **~$22/mo** |
| Elasticsearch PVC(s) | Managed Disk (Premium ZRS) | **Paid** |
| Loki PVC | Managed Disk | **~$2/mo** |
| Grafana PVC | Managed Disk | **~$1/mo** |
| Loki Azure Blob Storage | Azure Blob (log chunks + rules) | **~$1-5/mo** |
| Terraform State Storage | Azure Blob Storage (LRS) | **~$1/mo** |
| Entra ID App Registration (Grafana) | Azure AD | Free |

### Production Only (`beta_mode = false`)

| Resource | Azure Service | Cost |
|---|---|---|
| AKS Standard Tier | Uptime SLA (99.95%) | **$73/mo** |
| ES Node Pool (3x VMs) | Virtual Machines (memory-optimized) | **Paid** |
| ES OS Disks | Managed Disks | **Paid** |

### Optional

| Resource | Azure Service | Flag | Cost |
|---|---|---|---|
| Firecracker VM(s) | Virtual Machines + Public IPs | `enable_firecracker` | **~$144/mo per host** |
| Firecracker Storage Account | Azure Blob Storage | `enable_firecracker` | **~$1/mo** |

### Not Currently Provisioned (Future)

| Resource | Azure Service | Estimated cost |
|---|---|---|
| Azure Container Registry (Basic) | ACR | $5/mo |
| Azure Key Vault | Key Vault | ~$1/mo |
| Azure Front Door / WAF | CDN + WAF | $175+/mo |

---

## Beta (`beta_mode = true`, default)

Dedicated master + 3 data ES nodes on shared system pool. Production-like
ES topology without dedicated VMs. Uses Premium ZRS storage (same as
production) so the beta → production path requires no storage migration.
Includes observability stack (Loki + Alloy + Grafana) and Entra ID auth.

| Line Item | Spec | Count | Unit cost | Total/mo |
|---|---|---|---|---|
| System VMs | Standard_D4s_v5 (4 vCPU, 16 GB) | 3 | $140 | $420 |
| System OS Disks | Premium SSD, 128 GB | 3 | $19 | $57 |
| ES Master PVC | Premium ZRS, 16 GB | 1 | $10 | $10 |
| ES Data PVCs | Premium ZRS, 64 GB | 3 | $19 | $57 |
| LB + Public IP (kinotic-server) | Standard | 1 | $22 | $22 |
| LB + Public IP (Grafana) | Standard | 1 | $22 | $22 |
| Loki PVC | managed-csi-premium, 10 GB | 1 | $2 | $2 |
| Loki Blob Storage | Azure Blob (hot tier) | — | ~$0.02/GB | $3 |
| Grafana PVC | managed-csi-premium, 1 GB | 1 | $1 | $1 |
| DNS Zone | kinotic.ai | 1 | $0.50 | $1 |
| State Storage | Blob (LRS) | 1 | $1 | $1 |

### Beta Total: ~$596/mo

### Beta Resource Utilization (48 GB across 3 nodes)

| Pod | Memory | Count | Total |
|---|---|---|---|
| ES master | 2 GB | 1 | 2 GB |
| ES data | 8 GB | 3 | 24 GB |
| kinotic-server | 2 GB | 3 | 6 GB |
| Loki | 512 MB | 1 | 0.5 GB |
| Grafana | 256 MB | 1 | 0.25 GB |
| Alloy (per node) | 256 MB | 3 | 0.75 GB |
| System (kube, ECK, cert-mgr, Reloader) | — | — | ~3 GB |
| **Used** | | | **~36.5 GB** |
| **Available for rolling updates** | | | **~11.5 GB** |

---

## Production (`beta_mode = false`)

Dedicated ES node pool, 3 master + 3 data ES nodes, system pool tainted.
AKS Standard tier with uptime SLA.

| Line Item | Spec | Count | Unit cost | Total/mo |
|---|---|---|---|---|
| System VMs | Standard_D4s_v5 (4 vCPU, 16 GB) | 3 | $140 | $420 |
| System OS Disks | Premium SSD, 128 GB | 3 | $19 | $57 |
| ES VMs | Standard_E8s_v5 (8 vCPU, 64 GB) | 3 | $365 | $1,095 |
| ES OS Disks | Premium SSD, 256 GB | 3 | $38 | $114 |
| ES Master PVCs | Premium ZRS, 32 GB | 3 | $10 | $30 |
| ES Data PVCs | Premium ZRS, 256 GB | 3 | $74 | $222 |
| AKS Standard Tier | Uptime SLA (99.95%) | 1 | $73 | $73 |
| LB + Public IP (kinotic-server) | Standard | 1 | $22 | $22 |
| LB + Public IP (Grafana) | Standard | 1 | $22 | $22 |
| Loki PVC | managed-csi-premium, 10 GB | 1 | $2 | $2 |
| Loki Blob Storage | Azure Blob (hot tier) | — | ~$0.02/GB | $5 |
| Grafana PVC | managed-csi-premium, 1 GB | 1 | $1 | $1 |
| DNS Zone | kinotic.ai | 1 | $0.50 | $1 |
| State Storage | Blob (LRS) | 1 | $1 | $1 |

### Production Total: ~$2,065/mo

---

## Scaling from Beta to Production

```hcl
# In terraform.tfvars:
beta_mode = false
```

One `terraform apply`:
- Adds ES dedicated node pool (3x Standard_E8s_v5 across 3 AZs)
- Switches eck-stack values from `values-azure-beta.yaml` to `values-azure.yaml`
- ECK migrates ES pods from system pool to dedicated pool
- System pool gets `CriticalAddonsOnly` taint
- AKS tier upgrades from Free to Standard (SLA)

## Cost Reduction Options

| Optimization | Savings | Trade-off |
|---|---|---|
| Reserved Instances (1yr, system VMs) | ~30% on compute | Commitment |
| Reserved Instances (1yr, ES VMs) | ~30% on compute | Commitment |
| Spot instances (non-critical workloads) | ~60-90% on compute | Can be evicted |
| Smaller ES VMs (E4s_v5 instead of E8s_v5) | ~$550/mo | Less memory per node |
