# KinD Local Development Cluster

Local Kubernetes development environment for Kinotic using KinD (Kubernetes in Docker) and Terraform.

## Prerequisites

**Required:**
- [Docker](https://docs.docker.com/get-docker/) (Docker Desktop or Docker Engine)
- [Terraform](https://developer.hashicorp.com/terraform/install) (>= 1.5)

**Optional:**
- [kubectl](https://kubernetes.io/docs/tasks/tools/) — for debugging, logs, port-forwarding
- [mkcert](https://github.com/FiloSottile/mkcert) — browser-trusted local TLS (no cert warnings)

```bash
# macOS
brew install terraform kubectl
brew install --cask docker

# Optional: browser-trusted certs
brew install mkcert nss && mkcert -install
```

Run `./setup.sh` to verify prerequisites.

## Quick Start

```bash
cd deployment/kind

# Check prerequisites
./setup.sh

# Create cluster and deploy everything
cd terraform
terraform init
terraform apply

# With Keycloak/OIDC
terraform apply -var="enable_keycloak=true"

# Specific image version
terraform apply -var="kinotic_version=4.2.0-SNAPSHOT"

# Tear down
terraform destroy
```

## Architecture

No ingress controller or reverse proxy. Vert.x handles TLS directly in each pod,
matching the Azure production deployment pattern. KinD `extraPortMappings` route
host ports through NodePort services to the pods.

```
localhost:443   ──> NodePort 30443 ──> kinotic-server (Vert.x TLS, Web UI)
localhost:8080  ──> NodePort 30080 ──> kinotic-server (Vert.x TLS, OpenAPI)
localhost:4000  ──> NodePort 30400 ──> kinotic-server (Vert.x TLS, GraphQL)
localhost:58503 ──> NodePort 30503 ──> kinotic-server (Vert.x TLS, STOMP/WS)
localhost:8888  ──> NodePort 30888 ──> keycloak       (Keycloak TLS, when enabled)
localhost:3000  ─���> NodePort 30300 ──> grafana        (Grafana TLS)
```

### Namespaces

| Namespace | Contents |
|---|---|
| `elastic-system` | ECK operator |
| `elastic` | Elasticsearch cluster |
| `kinotic` | Kinotic server, TLS secret, Keycloak, PostgreSQL, load generator |
| `observability` | Loki, Alloy, Grafana |

## Service Access

### With mkcert (default)

| Service | URL |
|---------|-----|
| Kinotic UI | https://localhost/ |
| OpenAPI | https://localhost:8080/api/ |
| GraphQL | https://localhost:4000/graphql/ |
| WebSocket (STOMP) | wss://localhost:58503/v1 |
| Keycloak Admin | https://localhost:8888/auth/admin |
| Grafana | https://localhost:3000/ |

### Without mkcert (`-var="use_mkcert=false"`)

| Service | URL |
|---------|-----|
| Kinotic UI | http://localhost:9090/ |
| OpenAPI | http://localhost:8080/api/ |
| GraphQL | http://localhost:4000/graphql/ |
| WebSocket (STOMP) | ws://localhost:58503/v1 |
| Grafana | http://localhost:3000/ |

### Direct Access (kubectl port-forward)

```bash
kubectl port-forward svc/kinotic-es-es-http -n elastic 9200:9200    # Elasticsearch
kubectl port-forward svc/keycloak-db-postgresql -n kinotic 5432:5432  # PostgreSQL (with Keycloak)
```

## TLS (mkcert)

With mkcert installed, Terraform automatically generates browser-trusted certificates
and mounts them into pods. Vert.x and Keycloak read the PEM files directly.
No manual steps needed -- just ensure the CA is set up once:

```bash
brew install mkcert nss && mkcert -install
```

To disable TLS: `terraform apply -var="use_mkcert=false"`

### CLI Tools

```bash
# Add to .zshrc / .bashrc for tools like curl and Node.js
export NODE_EXTRA_CA_CERTS=~/.kinotic/kind/ca.crt
```

## OIDC / Keycloak

Keycloak provides OIDC authentication. When enabled, Terraform deploys PostgreSQL + Keycloak
and redeploys kinotic-server with the `kubernetes-oidc` Spring profile.

```bash
terraform apply -var="enable_keycloak=true"
```

If the cluster is already running, re-running with `enable_keycloak=true` will deploy
Keycloak and redeploy kinotic-server with OIDC -- no manual steps needed.

**Credentials:**

| Service | URL | Credentials |
|---------|-----|-------------|
| Kinotic (OIDC login) | https://localhost/login | testuser@example.com / password123 |
| Keycloak Admin | https://localhost:8888/auth/admin | admin / admin |

## Terraform Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `cluster_name` | `kinotic-cluster` | KinD cluster name |
| `kinotic_version` | `latest` | Kinotic server image tag |
| `worker_count` | `3` | Number of worker nodes |
| `enable_keycloak` | `false` | Deploy Keycloak + PostgreSQL for OIDC |
| `enable_load_generator` | `false` | Run load generator via Terraform |
| `use_mkcert` | `true` | Generate browser-trusted certs with mkcert |
| `deploy_timeout` | `600` | Helm release timeout (seconds) |

## Load Generator

The load generator creates sample entity definitions and data for testing.
It connects directly to the kinotic-server service and runs as a Kubernetes Job.

**Note:** The load generator currently uses basic (default) authentication. It cannot
run when Keycloak/OIDC is enabled as the sole auth provider — run the load generator
first to seed data, then enable Keycloak. Bearer token support for the load generator
is planned.

### Via Terraform

```bash
terraform apply -var="enable_load_generator=true"
```

### Via Helm (standalone)

```bash
cd deployment/kind

helm install load-generator ../helm/load-generator \
  -f config/load-generator/values.yaml \
  --kube-context kind-kinotic-cluster

# Watch progress
kubectl logs -l app.kubernetes.io/name=kinotic-load-generator -f

# Clean up (required before re-running -- Jobs are immutable)
helm uninstall load-generator --kube-context kind-kinotic-cluster
```

### Configuration

Override values in `config/load-generator/values.yaml`:

| Value | Default (KinD) | Description |
|-------|----------------|-------------|
| `kinotic.host` | `kinotic-server` | Service hostname (cluster-internal) |
| `kinotic.port` | `58503` | STOMP port |
| `kinotic.useSsl` | `false` | Use TLS (false for cluster-internal) |
| `loadGenerator.config.testName` | `generateComplexEntities` | Test to run |
| `loadGenerator.config.maxConcurrentRequests` | `1` | Parallel requests |
| `loadGenerator.config.maxRequestsPerSecond` | `100` | Rate limit |

## Testing Local Changes

```bash
# Build image locally (requires Java 21 + Gradle)
./gradlew :kinotic-server:bootBuildImage

# Load into running cluster
kind load docker-image kinoticai/kinotic-server:4.2.0-SNAPSHOT --name kinotic-cluster

# Restart to pick up new image
kubectl rollout restart deployment/kinotic-server
```

## Troubleshooting

**Port conflicts:**
```bash
lsof -i :443
lsof -i :8080
lsof -i :58503
```

**Pods stuck in ImagePullBackOff:**
```bash
kubectl describe pod <pod-name>
# Check image tag matches what's on Docker Hub
```

**View logs:**
```bash
kubectl logs -l app=kinotic-server -n kinotic -f
kubectl logs -l app=keycloak -n kinotic -f        # if Keycloak enabled
```

**Check cluster state:**
```bash
kubectl get pods,svc -n kinotic
kubectl get pods,svc -n elastic
kubectl get pods,svc -n observability
kubectl get elasticsearch -n elastic
```

**Clean slate:**
```bash
kind delete cluster --name kinotic-cluster
cd terraform
rm -rf .terraform terraform.tfstate terraform.tfstate.backup .terraform.lock.hcl
terraform init
terraform apply
```

## Files

```
deployment/kind/
├── setup.sh                         # One-time environment setup (prerequisites, mkcert CA)
├── terraform/
│   ├── main.tf                      # KinD cluster + providers + port mappings
│   ├── tls.tf                       # mkcert certificate generation
│   ├── elasticsearch.tf             # ECK operator + Elasticsearch (eck-stack chart)
│   ├── kinotic.tf                   # Kinotic server (NodePort + TLS)
│   ├── observability.tf             # Loki + Alloy + Grafana (TLS)
│   ├── keycloak.tf                  # PostgreSQL + Keycloak (conditional, NodePort + TLS)
│   ├── load-generator.tf            # Load generator (conditional)
│   ├── variables.tf                 # Input variables
│   └── outputs.tf                   # Cluster info + access URLs
├── config/                          # Helm values overrides for KinD
│   ├── eck-operator/values.yaml
│   ├── keycloak/values.yaml
│   ├── kinotic-server/values.yaml
│   ├── load-generator/values.yaml
│   └── postgresql/values.yaml
└── charts/keycloak/                 # Local Keycloak Helm chart (with TLS support)
```
