# KinD Local Development Cluster

Local Kubernetes development environment for Kinotic using KinD (Kubernetes in Docker) and Terraform.

## Setup

The setup script installs all required tools via Homebrew and configures mkcert
for browser-trusted TLS certificates:

```bash
cd deployment/kind
./setup.sh
```

This installs: Docker (or Colima), Terraform, kubectl, Helm, KinD, mkcert, jq, nss.
It also installs the mkcert local CA into your system trust store.

To check what's installed without making changes: `./setup.sh --check`

### Manual install (if you prefer)

```bash
brew install terraform kubectl helm kind mkcert nss jq
brew install --cask docker    # or: brew install docker colima && colima start
mkcert -install               # one-time CA setup
```

## Quick Start

```bash
cd deployment/kind

# Install tools + configure mkcert (one-time)
./setup.sh

# Create cluster and deploy everything
cd terraform
terraform init
terraform apply

# With Keycloak/OIDC
terraform apply -var="enable_keycloak=true"

# Rebuild and reload the servers after code changes (or only some: ../dev-reload.sh app)
../dev-reload.sh

# Tear down
terraform destroy
```

## Architecture

No ingress controller or reverse proxy. Vert.x handles TLS directly in each pod,
matching the Azure production deployment pattern. KinD `extraPortMappings` route
host ports through NodePort services to the pods.

```
localhost:443   ──> NodePort 30443 ──> kinotic-server-management (Vert.x TLS, the portal)
localhost:9090  ──> NodePort 30090 ──> kinotic-server-management (plain HTTP, when use_mkcert=false)
localhost:58503 ──> NodePort 30503 ──> kinotic-server-management (Vert.x TLS, REST and STOMP/WS)
localhost:58504 ──> NodePort 30504 ──> kinotic-server-system     (Vert.x TLS, REST and STOMP/WS)
localhost:58505 ──> NodePort 30505 ──> kinotic-server-app        (Vert.x TLS, REST and STOMP/WS)
localhost:8888  ──> NodePort 30888 ──> keycloak                  (Keycloak TLS, when enabled)
localhost:3000  ──> NodePort 30300 ──> grafana                   (Grafana TLS)
```

The three servers form one Ignite cluster through the headless `kinotic` Service, which selects
every server's pods.

### Namespaces

| Namespace | Contents |
|---|---|
| `elastic-system` | ECK operator |
| `elastic` | Elasticsearch cluster |
| `kinotic` | The Kinotic servers, TLS secret, Keycloak, PostgreSQL, load generator |
| `observability` | Loki, Alloy, Grafana |

## Service Access

### With mkcert (default)

| Service | URL |
|---------|-----|
| Kinotic UI | https://localhost/ |
| Management server (STOMP) | wss://localhost:58503/v1 |
| System server (STOMP) | wss://localhost:58504/v1 |
| App server (STOMP) | wss://localhost:58505/v1 |
| Keycloak Admin | https://localhost:8888/auth/admin |
| Grafana | https://localhost:3000/ |

Each application's API host, `https://<organizationId>--<applicationId>.localhost:58505`, is outside the
mkcert certificate, which names `localhost`, `kinotic.local`, `127.0.0.1` and `::1` alone, so a UI calling
its application's host, or an application's OAuth flow, works only without mkcert.

### Without mkcert (`-var="use_mkcert=false"`)

| Service | URL |
|---------|-----|
| Kinotic UI | http://localhost:9090/ |
| Management server (STOMP) | ws://localhost:58503/v1 |
| System server (STOMP) | ws://localhost:58504/v1 |
| App server (STOMP) | ws://localhost:58505/v1 |
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

## Project deployments

KinD has no Azure storage accounts, so the system server runs with
`kinotic.systemApi.disableAzureStorage` set (`config/kinotic/values.yaml`): a published UI is marked
ready without being uploaded or served.

A deployment's workloads run in micro VMs on a vm-manager node outside the cluster. They dial the
management server (sync, SBOM) and the app server (runtime) at `192.168.127.254`, the host alias through
which a BOXLITE node on this machine reaches the host, on the ports KinD maps there. A
CLOUD_HYPERVISOR node reaches the host at `172.17.0.1` instead; set
`KINOTIC_SYSTEMAPI_DEPLOYMENT_MANAGEMENTSERVER_HOST` and `_APPSERVER_HOST` in the system server's
`extraEnv` to match. The mkcert certificate does not name either address, so the workloads reach
the servers only without mkcert.

## OIDC / Keycloak

Keycloak is an OIDC provider to test social sign-in against. When enabled, Terraform deploys
PostgreSQL + Keycloak with the test realm from `deployment/docker-compose/keycloak-test-realm.json`
(realm `test`, client `kinotic-client`, user `testuser@example.com` / `password123`), and gives
the management server the client's secret under the secret name `keycloak`, the way the compose Keycloak
overlay does.

```bash
terraform apply -var="enable_keycloak=true"
```

If the cluster is already running, re-running with `enable_keycloak=true` deploys Keycloak and
redeploys the management server with the secret.

Signing in through it also needs an OIDC configuration whose authority is the realm and whose
secret name is `keycloak`; none is seeded. Keycloak publishes its issuer as
`https://localhost:8888/auth/realms/test`, which the browser reaches through the port mapping
and the management server pod does not, so the token exchange fails until Keycloak is published under a
host both resolve, as the compose stack does with the `keycloak` host.

| Service | URL | Credentials |
|---------|-----|-------------|
| Keycloak Admin | https://localhost:8888/auth/admin | admin / admin |

## Terraform Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `cluster_name` | `kinotic-cluster` | KinD cluster name |
| `node_image` | `""` | KinD node image override (empty = provider default) |
| `kinotic_version` | `5.0.0-SNAPSHOT`, set in `terraform.tfvars` | Kinotic server and migration image tag, the `kinoticVersion` CI publishes the images at |
| `worker_count` | `3` | Number of worker nodes |
| `enable_keycloak` | `false` | Deploy Keycloak + PostgreSQL with the test realm |
| `enable_load_generator` | `false` | Run load generator via Terraform |
| `use_mkcert` | `true` | Generate browser-trusted certs with mkcert (declared in `tls.tf`) |
| `keycloak_db_username` | `keycloak` | Keycloak PostgreSQL user |
| `keycloak_db_password` | `keycloak` | Keycloak PostgreSQL password |
| `keycloak_admin_password` | `admin` | Keycloak admin console password |
| `deploy_timeout` | `600` | Helm release timeout (seconds) |

## Load Generator

The load generator creates sample entity definitions and data for testing.
It connects directly to the management server's service and runs as a Kubernetes Job.

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
| `kinotic.host` | `kinotic-server-management` | Service hostname (cluster-internal) |
| `kinotic.port` | `58503` | STOMP port |
| `kinotic.useSsl` | `true` | Use TLS — the pods serve TLS even cluster-internally |
| `kinotic.tlsInsecure` | `true` | Skip verification so the mkcert CA isn't needed in the Job |
| `loadGenerator.config.testName` | `generateComplexEntities` | Test to run |
| `loadGenerator.config.maxConcurrentRequests` | `1` | Parallel requests |
| `loadGenerator.config.maxRequestsPerSecond` | `100` | Rate limit |

## Testing Local Changes

```bash
# Build a server's image locally (requires JDK 25 + Gradle)
./gradlew :kinotic-server-app:bootBuildImage

# Load into running cluster
kind load docker-image kinoticai/kinotic-server-app:5.0.0-SNAPSHOT --name kinotic-cluster

# Restart to pick up new image
kubectl rollout restart deployment/kinotic-server-app -n kinotic
```

## Troubleshooting

**Port conflicts:**
```bash
lsof -i :443
lsof -i :9090
lsof -i :58503
lsof -i :58504
lsof -i :58505
```

**Pods stuck in ImagePullBackOff:**
```bash
kubectl describe pod <pod-name>
# Check image tag matches what's on Docker Hub
```

**View logs:**
```bash
# Each server's pods carry its name as the `app` label, and every server's the part-of label
kubectl logs -l app=kinotic-server-management -n kinotic -f
kubectl logs -l app.kubernetes.io/part-of=kinotic -n kinotic -f --max-log-requests 10
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
├── dev-reload.sh                    # Rebuild the servers' images and reload them into the cluster
├── terraform/
│   ├── main.tf                      # KinD cluster + providers + port mappings
│   ├── tls.tf                       # mkcert certificate generation (declares use_mkcert)
│   ├── platform-secrets.tf          # Secret of JWT signing keys, secret-storage master key, GitHub App secrets
│   ├── elasticsearch.tf             # ECK operator + Elasticsearch (eck-stack chart)
│   ├── kinotic.tf                   # The Kinotic servers (NodePort + TLS)
│   ├── observability.tf             # Loki + Alloy + Grafana (TLS)
│   ├── keycloak.tf                  # PostgreSQL + Keycloak (conditional, NodePort + TLS)
│   ├── load-generator.tf            # Load generator (conditional)
│   ├── variables.tf                 # Input variables
│   ├── terraform.tfvars             # Default values for this cluster
│   └── outputs.tf                   # Cluster info + access URLs
├── config/                          # Helm values overrides for KinD
│   ├── eck-operator/values.yaml
│   ├── keycloak/values.yaml
│   ├── kinotic/values.yaml
│   ├── load-generator/values.yaml
│   └── postgresql/values.yaml
└── charts/keycloak/                 # Local Keycloak Helm chart (with TLS support)
```
