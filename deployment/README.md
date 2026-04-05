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
│   └── values-azure.yaml
├── kinotic/            # Kinotic server (Deployment, Service, ConfigMap, RBAC)
└── load-generator/     # Load testing Job
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

Keycloak also reads from the same TLS secret when enabled.

## Quick Reference

### KinD

```bash
cd deployment/kind/terraform
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

See [terraform/azure/README.md](terraform/azure/README.md) for first-time setup and day-2 operations.

## Firecracker

`terraform/azure/` optionally deploys VM hosts with KVM and Firecracker for secure multi-tenant customer workloads. These VMs share the same VNet as AKS and can reach Elasticsearch and kinotic-server directly.

Scripts for building Firecracker VM images (kernel, rootfs, overlay) are in `firecracker/`.
