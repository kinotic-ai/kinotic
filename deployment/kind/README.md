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

## Service Access

### Via Ingress (HTTPS)

| Service | URL |
|---------|-----|
| Kinotic UI | https://localhost/ |
| Kinotic API | https://localhost/api/ |
| GraphQL | https://localhost/graphql/ |
| WebSocket | wss://localhost/v1 |

### Direct Access (kubectl port-forward)

```bash
kubectl port-forward svc/kinotic-es-es-http 9200:9200    # Elasticsearch
kubectl port-forward svc/keycloak-db-postgresql 5432:5432  # PostgreSQL (with Keycloak)
kubectl port-forward svc/keycloak 8888:8888                # Keycloak (with Keycloak)
```

## Optional Setup

### Browser-Trusted TLS (mkcert)

With mkcert installed, Terraform automatically generates browser-trusted certificates. No manual steps needed — just ensure the CA is set up once:

```bash
brew install mkcert nss && mkcert -install
# Or: ./setup.sh --all (does this + /etc/hosts)
```

To fall back to cert-manager self-signed certs (browser warnings): `terraform apply -var="use_mkcert=false"`

### OIDC / Keycloak

Keycloak provides OIDC authentication. When enabled, Terraform deploys PostgreSQL + Keycloak and
redeploys kinotic-server with the `kubernetes-oidc` Spring profile and `oidc.enabled=true`.

```bash
# 1. Add kinotic.local to /etc/hosts (required — OIDC browser redirects use this hostname)
./setup.sh --hosts

# 2. Deploy with Keycloak (first deploy or adding to existing cluster)
cd terraform
terraform apply -var="enable_keycloak=true"
```

If the cluster is already running without Keycloak, re-running `terraform apply -var="enable_keycloak=true"`
will deploy Keycloak and **redeploy kinotic-server** with the OIDC configuration — no manual steps needed.

**Access:**

| Service | URL                               | Credentials |
|---------|-----------------------------------|-------------|
| Kinotic (OIDC login) | https://kinotic.local/login       | testuser@example.com / password123 |
| Keycloak Admin Console | https://kinotic.local/auth/admin | admin / admin |

To access the Keycloak admin console directly: `kubectl port-forward svc/keycloak 8888:8888`

### CLI Tools with Self-Signed Certs

```bash
# Add to .zshrc / .bashrc
export NODE_EXTRA_CA_CERTS=~/.kinotic/kind/ca.crt
```

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

The load generator creates sample entity definitions and data for testing. It connects to the Kinotic server through the ingress and runs as a Kubernetes Job.

### Via Terraform

```bash
terraform apply -var="enable_load_generator=true"
```

### Via Helm (standalone)

Useful for re-running after the cluster is already up, or with custom configuration:

```bash
cd deployment/kind

# Deploy (runs the generateComplexEntities test)
helm install load-generator ../helm/load-generator \
  -f config/load-generator/values.yaml \
  --kube-context kind-kinotic-cluster

# Watch progress
kubectl logs -l app.kubernetes.io/name=kinotic-load-generator -f

# Clean up when done (required before re-running — Jobs are immutable)
helm uninstall load-generator --kube-context kind-kinotic-cluster
```

### Configuration

Override values in `config/load-generator/values.yaml`:

| Value | Default (KinD) | Description |
|-------|----------------|-------------|
| `kinotic.host` | `kinotic.local` | Server hostname |
| `kinotic.port` | `443` | Server port |
| `kinotic.useSsl` | `true` | Use HTTPS/WSS |
| `kinotic.tlsInsecure` | `true` | Skip cert validation |
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

**Port conflicts on 80/443:**
```bash
lsof -i :80
lsof -i :443
```

**Pods stuck in ImagePullBackOff:**
```bash
kubectl describe pod <pod-name>
# Check image tag matches what's on Docker Hub
```

**View logs:**
```bash
kubectl logs -l app.kubernetes.io/name=kinotic -f
```

**Check cluster state:**
```bash
kubectl get pods,svc,ingress
kubectl get elasticsearch
```

**Clean slate:**
```bash
cd terraform
terraform destroy
terraform apply
```

## Files

```
deployment/kind/
├── setup.sh                         # One-time environment setup (prerequisites, /etc/hosts, mkcert CA)
├── terraform/
│   ├── main.tf                      # KinD cluster + providers
│   ├── tls.tf                       # mkcert certificate generation
│   ├── ingress.tf                   # ingress-nginx + cert-manager + CoreDNS
│   ├── elasticsearch.tf             # ECK operator + Elasticsearch
│   ├── kinotic.tf                   # Kinotic server
│   ├── keycloak.tf                  # PostgreSQL + Keycloak (conditional)
│   ├── load-generator.tf            # Load generator (conditional)
│   ├── variables.tf                 # Input variables
│   └── outputs.tf                   # Cluster info + access URLs
├── config/                          # Helm values overrides for KinD
│   ├── cert-manager/values.yaml
│   ├── eck-operator/values.yaml
│   ├── elasticsearch/values.yaml
│   ├── ingress-nginx/values.yaml
│   ├── keycloak/values.yaml
│   ├── load-generator/values.yaml
│   ├── postgresql/values.yaml
│   └── kinotic-server/values.yaml
└── charts/keycloak/                 # Local Keycloak Helm chart
```
