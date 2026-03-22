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

By default, cert-manager generates self-signed certificates (browser warnings). For trusted certs:

```bash
# After terraform apply has created the cluster
./setup.sh --mkcert
```

### OIDC / Keycloak

```bash
# Add hostname for OIDC browser flows
./setup.sh --hosts

# Deploy with Keycloak
cd terraform
terraform apply -var="enable_keycloak=true"
```

### CLI Tools with Self-Signed Certs

```bash
# Add to .zshrc / .bashrc
export NODE_EXTRA_CA_CERTS=~/.kinotic/kind/ca.crt
```

## Terraform Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `cluster_name` | `kinotic-cluster` | KinD cluster name |
| `kinotic_version` | `4.2.0-SNAPSHOT` | Kinotic server image tag |
| `worker_count` | `3` | Number of worker nodes |
| `enable_keycloak` | `false` | Deploy Keycloak + PostgreSQL for OIDC |
| `enable_load_generator` | `false` | Run load generator after deploy |
| `deploy_timeout` | `600` | Helm release timeout (seconds) |

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
├── setup.sh                         # One-time environment setup
├── terraform/
│   ├── main.tf                      # KinD cluster + providers
│   ├── ingress.tf                   # ingress-nginx + cert-manager
│   ├── elasticsearch.tf             # ECK operator + Elasticsearch
│   ├── kinotic.tf                   # Kinotic server
│   ├── keycloak.tf                  # PostgreSQL + Keycloak (conditional)
│   ├── load-generator.tf            # Load generator (conditional)
│   ├── variables.tf                 # Input variables
│   ├── outputs.tf                   # Cluster info + access URLs
│   └── terraform.tfvars             # Default values
├── config/
│   ├── kind-config.yaml             # KinD cluster topology
│   ├── cert-manager/values.yaml
│   ├── eck-operator/values.yaml
│   ├── elasticsearch/values.yaml
│   ├── ingress-nginx/values.yaml
│   ├── keycloak/values.yaml
│   ├── postgresql/values.yaml
│   └── kinotic-server/values.yaml
└── charts/keycloak/                 # Local Keycloak Helm chart
```
