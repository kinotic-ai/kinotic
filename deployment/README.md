# Deployment

## Environments

| Environment | Directory | Purpose |
|---|---|---|
| **Docker Compose** | `docker-compose/` | Local development (Elasticsearch, OpenFGA, the observability stack, Keycloak); the development server runs the same images and config files as containers |
| **KinD** | `kind/` | Local Kubernetes via Kubernetes in Docker, for rehearsing the Helm charts |
| **Development server** | `terraform/proxmox/` + `terraform/azure/dev-server/` | One Proxmox host with a container per service, workload nodes on their own machines, Front Door and email kept in Azure ([design](https://kinotic.ai/platform/development-server)) |
| **Developer's Azure side** | `terraform/azure/dev/` | Front Door, sites account, and email for the servers on a developer machine |
| **Azure** | `terraform/azure/` | Production AKS cluster |

KinD and Azure are deployed with Terraform and share the same Helm charts. The development
server is deployed with Terraform and shares the images and config files with local development.

## Workload nodes

`vm-node/` provisions a node that runs workloads as Cloud Hypervisor micro VMs — Docker with
the Kata runtime, XFS project quotas, the firewall floor — and installs the vm-manager as a
service. The development server's nodes and any other node run it by hand; its README says
what each step establishes.

## Shared Helm Charts

```
helm/
├── eck-stack/          # Elasticsearch via the official elastic/eck-stack chart
│   ├── values.yaml     # Base: version, cluster name, Kibana off
│   ├── values-kind.yaml
│   ├── values-azure.yaml
│   └── values-azure-beta.yaml
├── kinotic/            # The org, system and app servers (a Deployment, Service and ConfigMap each), one Ignite cluster, the migration and store Jobs, RBAC
├── openfga/            # Values for the upstream openfga chart: the authorization engine; its Postgres is in the cluster in KinD, a managed server in Azure
├── es-secret-sync/     # ES credential copy (elastic → kinotic namespace)
├── mimir/              # Mimir in monolithic mode (-target=all) on a persistent volume
├── load-generator/     # Load testing Job
└── observability/      # Loki, Tempo, Alloy, Grafana values + Alloy pipeline config
```

Charts use a **layered values** pattern. The base `values.yaml` contains defaults that work across environments. Environment-specific files override what differs (resource limits, TLS, service type, storage classes, node topology).

To add a new environment, create a new values overlay file and reference it from your terraform.

## Namespace Convention

Both KinD and Azure use the same layout:

| Namespace | Contents |
|---|---|
| `elastic-system` | ECK operator |
| `elastic` | Elasticsearch cluster |
| `kinotic` | The Kinotic servers, TLS certs, OpenFGA and its Postgres, Keycloak (when enabled), load generator |
| `observability` | Loki, Tempo, Mimir, Alloy, Grafana |

## Network Policy

Both environments enforce `NetworkPolicy` resources to restrict Elasticsearch access:

- **KinD** — uses kindnet (supports NetworkPolicy since KinD v0.23+)
- **Azure** — uses Azure CNI with Cilium (`network_data_plane = "cilium"`), which replaces Calico as the network policy engine. Cilium is eBPF-based, built into AKS, and fully managed by Azure.

The ES NetworkPolicy allows ingress only from the `kinotic` namespace (the servers + migration) and `elastic-system` (ECK operator). All other namespaces are blocked.

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

Centralized logs, traces and metrics using Grafana's stack:

- **Alloy** — DaemonSet on each node, collects pod logs, ships to Loki under the `kinotic-system` tenant. Pipeline config in `helm/observability/alloy-config.alloy`.
- **Loki** — Multi-tenant log storage (`auth_enabled: true`): one tenant per organization for workload logs, plus the reserved `kinotic-system` tenant for platform logs. Filesystem in KinD, Azure Blob Storage in Azure.
- **Tempo** — Multi-tenant trace storage (`multitenancy_enabled: true`), the grafana/tempo single-binary chart with `values-tempo.yaml`. It receives OTLP on 4317/4318 and serves its HTTP API on 3100; its metrics-generator writes each tenant's span metrics to Mimir under the same tenant. Traces are kept 30 days: on a persistent volume in KinD, in Azure Blob Storage in Azure (`values-tempo-azure.yaml`).
- **Mimir** — Multi-tenant metrics storage, the local `helm/mimir` chart running one monolithic process. It receives OTLP at `/otlp` and serves the Prometheus API under `/prometheus` on 9009. Blocks are kept 30 days: on a persistent volume in KinD, in Azure Blob Storage in Azure (`helm/mimir/values-azure.yaml`).

On Azure the three stores share one storage account, `st<prefix>obs`, each in containers of its own (`loki-chunks`, `loki-ruler`, `tempo-traces`, `mimir-blocks`, `mimir-ruler`), and each authenticates as a workload identity of its own (`id-<prefix>-loki`, `-tempo`, `-mimir`) holding Storage Blob Data Contributor on its containers alone. The account takes no public traffic and no shared keys: the stores reach it through its private endpoint in the VNet's private-endpoints subnet, resolved by the `privatelink.blob.core.windows.net` zone linked to the VNet, with Entra ID tokens. Terraform manages it through the resource manager alone (`data_plane_available = false` in the cluster root's provider). Their persistent volumes hold only write-ahead logs and caches.
- **Grafana** — Query and dashboards, with Loki, Tempo and Mimir datasources linked to each other. Each datasource browses the `kinotic-system` tenant by default; Loki's multi-tenant queries accept pipe-separated ids (`acme|kinotic-system`). Local auth in KinD, Entra ID (Azure AD) in Azure.

Each server runs the OpenTelemetry Java agent its image embeds, exporting its traces to Tempo and its metrics to Mimir over OTLP/HTTP under the `kinotic-system` tenant (`otel.*` in the kinotic chart's values); its logs go through Alloy, so the agent exports none.

Customer workload (micro VM) logs are shipped separately: each vm-manager node runs its own Alloy process that tails per-workload log directories and routes each stream to the workload organization's tenant. See the observability page on the website for the architecture.

To add a new cluster log source, add a `local.file_match` + `loki.source.file` block to the Alloy config and apply.

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
cd deployment/terraform/azure/global && terraform init && terraform apply  # once
cd ../cluster && terraform init && terraform apply                         # infra
cd ../frontend && terraform init && terraform apply && ./deploy.sh         # SPA
```

Azure documentation:
- [README.md](terraform/azure/README.md) — Deploy guide (global → cluster → frontend)
- [OPS.md](terraform/azure/OPS.md) — Day-2 operations (scaling, upgrades, certs)
- [TROUBLESHOOTING.md](terraform/azure/TROUBLESHOOTING.md) — Common errors and fixes
- [PRODUCTION.md](terraform/azure/PRODUCTION.md) — Production readiness checklist
- [COST.md](terraform/azure/COST.md) — Cost projections (~$573/mo beta, ~$2,025/mo production)

### Development server

```bash
cd deployment/terraform/azure/dev-server && terraform init && terraform apply   # the Azure side
cd ../../proxmox
./generate-secrets.sh ./dev-server-secrets && ./sync-secrets.sh ./dev-server-secrets <host>   # secrets on the host first
terraform init && terraform apply                                              # the containers
```

See [terraform/proxmox/README.md](terraform/proxmox/README.md) for the whole runbook.

## Firecracker

`terraform/azure/` optionally deploys VM hosts with KVM and Firecracker for secure multi-tenant customer workloads. These VMs share the same VNet as AKS and can reach Elasticsearch and the servers directly.

Scripts for building Firecracker VM images (kernel, rootfs, overlay) are in `firecracker/`.
