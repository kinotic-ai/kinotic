# Deployment

## Environments

| Environment | Directory | Purpose |
|---|---|---|
| **KinD** | `kind/` | Local development and testing via Kubernetes in Docker |
| **Azure** | `terraform/azure/` | Production AKS cluster with optional Firecracker VM hosts |
| **Docker Compose** | `docker-compose/` | Lightweight local setup (Elasticsearch, PostgreSQL, Keycloak) |

Both KinD and Azure are deployed with Terraform and share the same Helm charts.

## Shared Helm Charts

```
helm/
├── eck-stack/          # Elasticsearch via the official elastic/eck-stack chart
│   ├── values.yaml     # Base: version, cluster name, Kibana off
│   ├── values-kind.yaml
│   ├── values-azure.yaml
│   └── values-azure-beta.yaml
├── kinotic/            # Kinotic server (Deployment, Service, ConfigMap, RBAC)
├── es-secret-sync/     # ES credential copy (elastic → kinotic namespace)
├── load-generator/     # Load testing Job
└── observability/      # Loki, Alloy, Grafana values + Alloy pipeline config
```

Charts use a **layered values** pattern. The base `values.yaml` contains defaults that work across environments. Environment-specific files override what differs (resource limits, TLS, service type, storage classes, node topology).

To add a new environment, create a new values overlay file and reference it from your terraform.

## Namespace Convention

Both KinD and Azure use the same layout:

| Namespace | Contents |
|---|---|
| `elastic-system` | ECK operator |
| `elastic` | Elasticsearch cluster |
| `kinotic` | Kinotic server, TLS certs, Keycloak (when enabled), load generator |
| `observability` | Loki, Alloy, Grafana |

## Network Policy

Both environments enforce `NetworkPolicy` resources to restrict Elasticsearch access:

- **KinD** — uses kindnet (supports NetworkPolicy since KinD v0.23+)
- **Azure** — uses Azure CNI with Cilium (`network_data_plane = "cilium"`), which replaces Calico as the network policy engine. Cilium is eBPF-based, built into AKS, and fully managed by Azure.

The ES NetworkPolicy allows ingress only from the `kinotic` namespace (server + migration) and `elastic-system` (ECK operator). All other namespaces are blocked.

## Elasticsearch Storage

Azure deployments use **Premium ZRS** (zone-redundant) storage for ES in both beta
and production. This is intentional — using the same StorageClass across tiers means
scaling from beta to production requires no storage migration. ECK handles the node
relocation automatically.

ES volumes can be expanded online with zero downtime by increasing the `storage:`
value in the eck-stack values and running `terraform apply`. You can only grow, never shrink.

## TLS Pattern

No ingress controller or reverse proxy in either environment. Vert.x handles TLS directly in each pod by reading PEM certificate files mounted from a Kubernetes Secret.

| Environment | Certificate Source | Rotation |
|---|---|---|
| KinD | mkcert (browser-trusted local CA) | N/A (dev) |
| Azure | cert-manager + Let's Encrypt (DNS-01 via Azure DNS) | Automatic (Reloader restarts pods) |

Keycloak and Grafana also read from the same TLS secret.

## Observability

Centralized log collection using Grafana's stack:

- **Alloy** — DaemonSet on each node, collects pod logs, ships to Loki. Pipeline config in `helm/observability/alloy-config.alloy`.
- **Loki** — Log storage. Filesystem in KinD, Azure Blob Storage in Azure.
- **Grafana** — Query and dashboards. Local auth in KinD, Entra ID (Azure AD) in Azure.

To add a new log source (e.g. Firecracker VM logs), add a `local.file_match` + `loki.source.file` block to the Alloy config and apply.

## Quick Reference

### KinD

```bash
cd deployment/kind
./setup.sh                            # one-time: install tools + mkcert CA
cd terraform
terraform init && terraform apply

# With OIDC:  terraform apply -var="enable_keycloak=true"
# Rebuild:    ../dev-reload.sh
# Tear down:  terraform destroy
```

See [kind/README.md](kind/README.md) for full details.

### Azure

```bash
cd deployment/terraform/azure
terraform init && terraform apply

# With Firecracker:    terraform apply -var="enable_firecracker=true"
# With load generator: terraform apply -var="enable_load_generator=true"
```

Azure documentation:
- [README.md](terraform/azure/README.md) — Deploy day guide
- [OPS.md](terraform/azure/OPS.md) — Day-2 operations (scaling, upgrades, certs)
- [TROUBLESHOOTING.md](terraform/azure/TROUBLESHOOTING.md) — Common errors and fixes
- [PRODUCTION.md](terraform/azure/PRODUCTION.md) — Production readiness checklist
- [COST.md](terraform/azure/COST.md) — Cost projections (beta ~$596/mo, production ~$2,065/mo)

## Firecracker

`terraform/azure/` optionally deploys VM hosts with KVM and Firecracker for secure multi-tenant customer workloads. These VMs share the same VNet as AKS and can reach Elasticsearch and kinotic-server directly.

Scripts for building Firecracker VM images (kernel, rootfs, overlay) are in `firecracker/`.
