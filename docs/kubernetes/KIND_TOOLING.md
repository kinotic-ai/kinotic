# KinD Cluster Developer Tools

Automation tools for creating and managing KinD (Kubernetes in Docker) clusters for local development and testing of Structures cluster coordination features.

## Quick Start

```bash
# Navigate to project root
cd /path/to/structures

# Create a KinD cluster
./dev-tools/kind/kind-cluster.sh create

# Deploy structures-server (Elasticsearch only, no OIDC)
./dev-tools/kind/kind-cluster.sh deploy

# OR: Deploy with Keycloak for OIDC authentication
./dev-tools/kind/kind-cluster.sh deploy --with-keycloak

# Check status
./dev-tools/kind/kind-cluster.sh status

# View logs
./dev-tools/kind/kind-cluster.sh logs --follow

# Clean up
./dev-tools/kind/kind-cluster.sh delete
```

> **Note:** The `deploy` command automatically builds and loads the structures-server image.

## Service Access URLs

Once the cluster is deployed, services are accessible via **HTTPS** through the ingress or directly via **NodePort**:

### Via Ingress (HTTPS) - Recommended

| Service | URL | Notes |
|---------|-----|-------|
| **Structures UI** | https://localhost/ | Static SPA |
| **Structures API** | https://localhost/api/ | OpenAPI REST |
| **Structures GraphQL** | https://localhost/graphql/ | GraphQL endpoint |
| **Structures WebSocket** | wss://localhost/v1 | STOMP with sticky sessions |

### Via NodePort (Direct, no TLS)

| Service | URL | Credentials | Notes |
|---------|-----|-------------|-------|
| **Structures UI** | http://localhost:9090 | - | Always available |
| **Structures Health** | http://localhost:9090/health | - | Always available |
| **Structures OpenAPI** | http://localhost:8080 | - | Always available |
| **Structures GraphQL** | http://localhost:4000 | - | Always available |
| **Continuum Stomp** | ws://localhost:58503 | - | Always available |
| **Continuum REST** | http://localhost:58504 | - | Always available |
| **Elasticsearch** | http://localhost:9200 | - | With `--with-deps` (default) |
| **PostgreSQL** | localhost:5555 | keycloak / keycloak | With `--with-keycloak` only |
| **Keycloak Admin** | http://localhost:8888/auth/admin | admin / admin | With `--with-keycloak` only |

> **Note:** PostgreSQL and Keycloak are only deployed when using `--with-keycloak` (`-k`) flag.
> PostgreSQL uses port 5555 to avoid conflicts with locally installed PostgreSQL (default 5432).

## Prerequisites

The following tools must be installed:

- **Docker** - Container runtime (Docker Desktop or Docker Engine)
- **kind** - Kubernetes in Docker CLI
- **kubectl** - Kubernetes command-line tool
- **helm** - Kubernetes package manager

### Optional (Recommended for Local HTTPS)

- **mkcert** - Generate locally-trusted TLS certificates (no browser warnings)

The script will check for these prerequisites and provide installation instructions if any are missing.

### macOS Installation

```bash
# Install required tools via Homebrew
brew install kind kubectl helm

# Docker Desktop (provides Docker daemon)
brew install --cask docker

# Optional: mkcert for browser-trusted local HTTPS
brew install mkcert
brew install nss  # Required for Firefox support
mkcert -install   # Install local CA (one-time setup)
```

### Linux Installation

```bash
# kind
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.20.0/kind-linux-amd64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind

# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Docker (Ubuntu/Debian)
sudo apt-get update
sudo apt-get install docker.io
sudo systemctl start docker
sudo usermod -aG docker $USER

# Optional: mkcert for browser-trusted local HTTPS
# First install certutil (required for Firefox/Chrome)
sudo apt install libnss3-tools    # Ubuntu/Debian
# or: sudo yum install nss-tools  # Fedora/RHEL
# or: sudo pacman -S nss          # Arch

# Install mkcert via Homebrew on Linux
brew install mkcert

# Or install from pre-built binaries
curl -JLO "https://dl.filippo.io/mkcert/latest?for=linux/amd64"
chmod +x mkcert-v*-linux-amd64
sudo mv mkcert-v*-linux-amd64 /usr/local/bin/mkcert

# Install local CA (one-time setup)
mkcert -install
```

### About mkcert

[mkcert](https://github.com/FiloSottile/mkcert) is a simple tool for making locally-trusted development certificates. When installed:

- **With mkcert**: `https://localhost` works with no browser warnings
- **Without mkcert**: Uses cert-manager self-signed certs (browser shows "Not Secure" warning, but still works)

The deploy script automatically detects mkcert and generates browser-trusted certificates if available.

## Commands

### create - Create KinD Cluster

Create a new KinD cluster with multi-node topology suitable for testing cluster coordination.

```bash
./kind-cluster.sh create [options]
```

**Options:**
- `--name <name>` - Cluster name (default: structures-cluster)
- `--config <path>` - Path to kind-config.yaml (default: config/kind-config.yaml)
- `--k8s-version <version>` - Kubernetes version (default: latest)
- `--force` - Recreate if cluster exists
- `--skip-checks` - Skip prerequisite checks

**Examples:**

```bash
# Create with defaults (1 control-plane + 2 workers)
./kind-cluster.sh create

# Create with custom name
./kind-cluster.sh create --name test-cluster

# Recreate existing cluster
./kind-cluster.sh create --force

# Use specific Kubernetes version
./kind-cluster.sh create --k8s-version v1.28.0
```

**What it does:**
1. Validates prerequisites (Docker, kind, kubectl, helm)
2. Creates multi-node KinD cluster per configuration
3. Waits for all nodes to be Ready
4. Displays cluster information and next steps

**Time:** < 5 minutes

---

### deploy - Deploy structures-server

Deploy structures-server and its dependencies to the cluster.

```bash
./kind-cluster.sh deploy [options]
```

**Options:**
- `--name <name>` - Cluster name (default: structures-cluster)
- `--chart <path>` - Path to Helm chart (default: ./helm/structures)
- `--values <path>` - Path to values file (default: config/helm-values.yaml)
- `--set <key=value>` - Override Helm values (can be used multiple times)
- `--with-deps` - Deploy Elasticsearch dependency - **default**
- `--no-deps` - Skip dependencies (assumes already deployed)
- `--with-keycloak`, `-k` - Deploy Keycloak + PostgreSQL and enable OIDC authentication
- `--with-observability` - Deploy observability stack (OpenTelemetry, Prometheus, Grafana)
- `--wait-timeout <duration>` - Deployment timeout (default: 5m)

**Examples:**

```bash
# Deploy with Elasticsearch only (default - no OIDC)
./kind-cluster.sh deploy

# Deploy with Keycloak for OIDC authentication
./kind-cluster.sh deploy --with-keycloak
# or shorthand:
./kind-cluster.sh deploy -k

# Deploy without any dependencies (if already deployed)
./kind-cluster.sh deploy --no-deps

# Deploy with observability stack
./kind-cluster.sh deploy --with-observability

# Deploy with custom replica count
./kind-cluster.sh deploy --set replicaCount=3

# Deploy with custom values file
./kind-cluster.sh deploy --values my-values.yaml
```

**What it does:**
1. Verifies cluster exists and is accessible
2. Adds required Helm repositories (Bitnami, etc.)
3. Deploys NGINX Ingress Controller
4. Deploys Elasticsearch (always, unless --no-deps)
5. **If `--with-keycloak` is specified:**
   - Deploys PostgreSQL (for Keycloak backend)
   - Creates Keycloak realm ConfigMap from `docker-compose/keycloak-test-realm.json`
   - Deploys Keycloak with OIDC configuration
   - Enables OIDC in structures-server (`oidc.enabled=true`)
6. Builds and loads structures-server image
7. Deploys structures-server via Helm
8. Waits for all pods to be ready
9. Displays access URLs and next steps

**Time:** ~3 minutes (without Keycloak), ~5 minutes (with Keycloak)

**Access URLs:** See [Service Access URLs](#service-access-urls) section above.

---

### build - Build Docker Image

Build structures-server Docker image using Gradle's `bootBuildImage` task with Spring Boot Cloud Native Buildpacks.

```bash
./kind-cluster.sh build [options]
```

**Options:**
- `--module <name>` - Gradle module to build (default: structures-server)
- `--load` - Automatically load image into cluster after build

**Examples:**

```bash
# Build image
./kind-cluster.sh build

# Build and load into cluster
./kind-cluster.sh build --load

# Build specific module
./kind-cluster.sh build --module structures-server
```

**What it does:**
1. Runs `./gradlew :structures-server:bootBuildImage`
2. Uses Paketo Buildpacks for optimized Java images
3. Includes health checker buildpack
4. Tags image as `mindignited/structures-server:<version>` from gradle.properties
5. Optionally loads into cluster if `--load` flag provided

**Time:** 2-5 minutes (first build), < 1 minute (subsequent builds with cache)

---

### load - Load Image into Cluster

Load a locally built Docker image into KinD cluster nodes.

```bash
./kind-cluster.sh load [options]
```

**Options:**
- `--name <name>` - Cluster name (default: structures-cluster)
- `--image <name>` - Full image name (default: from gradle.properties)

**Examples:**

```bash
# Load default image
./kind-cluster.sh load

# Load specific image
./kind-cluster.sh load --image mindignited/structures-server:0.5.0

# Load into specific cluster
./kind-cluster.sh load --name test-cluster
```

**What it does:**
1. Verifies image exists locally
2. Loads image into all KinD cluster nodes using `kind load docker-image`
3. Verifies image is available on cluster nodes via `crictl`
4. Displays deployment instructions

**Time:** < 1 minute

---

### status - Display Cluster Status

Display cluster and deployment status information.

```bash
./kind-cluster.sh status [options]
```

**Options:**
- `--name <name>` - Cluster name (default: structures-cluster)
- `--watch, -w` - Watch status updates (refresh every 2 seconds)

**Examples:**

```bash
# Show current status
./kind-cluster.sh status

# Watch status updates
./kind-cluster.sh status --watch
```

**What it displays:**
- Cluster name, context, API server
- Node status (control-plane and workers)
- Helm releases with revision numbers
- Pod status with ready counts and restart counts
- Service endpoints and access URLs
- OIDC configuration status (if deployed)

---

### logs - View Pod Logs

Stream logs from structures-server pods.

```bash
./kind-cluster.sh logs [options]
```

**Options:**
- `--name <name>` - Cluster name (default: structures-cluster)
- `--follow, -f` - Follow log output (like `tail -f`)
- `--tail <n>` - Show last N lines (default: 100)
- `--selector, -l <sel>` - Pod selector (default: app=structures-server)

**Examples:**

```bash
# Show last 100 lines
./kind-cluster.sh logs

# Follow logs (Ctrl+C to stop)
./kind-cluster.sh logs --follow

# Show last 500 lines
./kind-cluster.sh logs --tail 500

# View logs from specific pods
./kind-cluster.sh logs --selector app=elasticsearch
```

---

### delete - Delete Cluster

Delete the KinD cluster and all resources.

```bash
./kind-cluster.sh delete [options]
```

**Options:**
- `--name <name>` - Cluster name (default: structures-cluster)
- `--force, -f` - Skip confirmation prompt

**Examples:**

```bash
# Delete with confirmation
./kind-cluster.sh delete

# Delete without confirmation
./kind-cluster.sh delete --force
```

**What it does:**
1. Verifies kubectl context is a KinD cluster (safety check)
2. Prompts for confirmation (unless `--force`)
3. Deletes cluster and all containers
4. Cleans up kubectl context

**Time:** < 2 minutes

---

## Ingress Architecture

The structures-server uses **two separate Ingress resources** with TLS/HTTPS:

| Ingress | Path | Backend | Features |
|---------|------|---------|----------|
| `structures-server-ws` | `/v1` | STOMP (58503) | WebSocket, sticky sessions, long timeouts |
| `structures-server-http` | `/api/*`, `/graphql/*`, `/*` | REST/GraphQL/UI | Path rewriting, standard timeouts |

This architecture:
- ✅ Works with default nginx-ingress security settings (no `configuration-snippet` needed)
- ✅ Provides HTTPS with automatic TLS certificate management
- ✅ Supports WebSocket connections with proper sticky sessions
- ✅ Handles path-based routing to multiple backend services

See [INGRESS_SETUP.md](INGRESS_SETUP.md) for detailed configuration information.

---

## Configuration

### KinD Cluster Configuration

Edit `dev-tools/kind/config/kind-config.yaml` to customize cluster topology:

```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30090
        hostPort: 9090  # structures-server
  - role: worker
  - role: worker  # Add/remove workers as needed
```

### Helm Values

Edit `dev-tools/kind/config/helm-values.yaml` to customize structures-server deployment.

Create `dev-tools/kind/config/helm-values.local.yaml` for personal overrides (gitignored).

### Environment Variables

```bash
# Cluster configuration
export KIND_CLUSTER_NAME=my-cluster
export KIND_CONFIG_PATH=path/to/kind-config.yaml

# Helm configuration
export HELM_CHART_PATH=./helm/structures
export HELM_VALUES_PATH=path/to/helm-values.yaml

# Feature flags
export DEPLOY_DEPS=1              # Deploy Elasticsearch dependency (default: 1)
export DEPLOY_KEYCLOAK=0          # Deploy Keycloak + PostgreSQL (default: 0)
export DEPLOY_OBSERVABILITY=0     # Deploy observability stack (default: 0)

# Behavior
export VERBOSE=1                  # Enable verbose logging
export DRY_RUN=1                  # Print commands without executing
export SKIP_CHECKS=1              # Skip prerequisite checks
```

---

## Workflow Examples

### Fresh Cluster Setup (Basic - No OIDC)

```bash
# Optional: Install mkcert for browser-trusted HTTPS (one-time)
brew install mkcert nss && mkcert -install

# Create cluster and deploy with Elasticsearch only
./kind-cluster.sh create
./kind-cluster.sh deploy

# Access structures-server via HTTPS
open https://localhost
```

### Fresh Cluster Setup (With Keycloak/OIDC)

```bash
# Create cluster and deploy with Keycloak for OIDC
./kind-cluster.sh create
./kind-cluster.sh deploy --with-keycloak

# Access structures-server (OIDC login required)
open https://localhost/login

# Access Keycloak admin console
open http://localhost:8888/auth/admin  # admin/admin
```

### Testing Local Changes

```bash
# Make code changes to structures-server
vim structures-server/src/main/java/...

# Build and deploy updated image
./kind-cluster.sh build --load
./kind-cluster.sh deploy --set image.pullPolicy=Never

# Tail logs to see changes
./kind-cluster.sh logs --follow
```

### Running Integration Tests

```bash
# Ensure cluster and dependencies are deployed
./kind-cluster.sh status

# Run integration tests against cluster
./gradlew :structures-core:integrationTest

# Check logs if tests fail
./kind-cluster.sh logs --tail 500
```

### Recreate Cluster (Clean Slate)

```bash
# Delete existing cluster
./kind-cluster.sh delete --force

# Create fresh cluster and deploy
./kind-cluster.sh create
./kind-cluster.sh deploy
```

---

## Troubleshooting

### Cluster Creation Fails

**Problem:** `kind create cluster` fails with port conflicts

**Solution:**
```bash
# Check for conflicting processes on ports 8080, 9090, 8888
lsof -i :8080
lsof -i :9090
lsof -i :8888

# Stop conflicting processes or edit config/kind-config.yaml to use different ports
```

**Problem:** Docker daemon not running

**Solution:**
```bash
# macOS: Start Docker Desktop app
# Linux: sudo systemctl start docker
```

### Deployment Fails

**Problem:** Admission webhook denies ingress with "configuration-snippet" or "risky annotation" error

```
Error: admission webhook "validate.nginx.ingress.kubernetes.io" denied the request: 
nginx.ingress.kubernetes.io/configuration-snippet annotation cannot be used
```
or
```
annotation group ConfigurationSnippet contains risky annotation based on ingress configuration
```

**Solution:**
```bash
# Patch nginx-ingress to allow snippets and risky annotations (development only!)
kubectl patch configmap ingress-nginx-controller \
  -n ingress-nginx \
  --context kind-structures-cluster \
  --type merge \
  -p '{"data":{"allow-snippet-annotations":"true","annotations-risk-level":"Critical"}}'

# Restart the ingress controller
kubectl rollout restart deployment ingress-nginx-controller \
  -n ingress-nginx \
  --context kind-structures-cluster

# Wait for it to be ready
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=60s \
  --context kind-structures-cluster

# Retry deployment
./kind-cluster.sh deploy
```

> **Note:** This is automatically handled by the deployment scripts for new clusters. See [Security Considerations](#security-considerations) for details.

---

**Problem:** Pods stuck in `Pending` or `ImagePullBackOff`

**Solution:**
```bash
# Check pod status
kubectl get pods

# Describe pod for details
kubectl describe pod <pod-name>

# Common fixes:
# 1. If ImagePullBackOff on locally built image:
./kind-cluster.sh load --image mindignited/structures-server:<version>

# 2. If resource constraints:
# Edit helm-values.yaml to reduce resource requests
```

**Problem:** Keycloak realm not imported

**Solution:**
```bash
# Check ConfigMap exists
kubectl get configmap keycloak-realm

# Manually import realm
kubectl port-forward svc/keycloak 8888:8888
# Navigate to http://localhost:8888/auth/admin
# Import realm manually from docker-compose/keycloak-test-realm.json
```

### Image Build Fails

**Problem:** `bootBuildImage` fails with Java version error

**Solution:**
```bash
# Check Java version (requires Java 21+)
java --version

# Use correct Java version
export JAVA_HOME=/path/to/java-21
./kind-cluster.sh build
```

**Problem:** Docker connection refused during build

**Solution:**
```bash
# Verify Docker is running
docker ps

# Check Docker socket permissions (Linux)
sudo chmod 666 /var/run/docker.sock
```

### Access Issues

**Problem:** Cannot access services at localhost URLs

**Solution:**
```bash
# Verify port mappings
kubectl get svc

# Check port forwards are active
docker ps | grep structures-cluster

# Restart cluster if needed
./kind-cluster.sh delete --force
./kind-cluster.sh create
```

---

## Advanced Usage

### Custom Kubernetes Version

```bash
# List available versions
docker search kindest/node

# Create cluster with specific version
./kind-cluster.sh create --k8s-version v1.28.0
```

### Multiple Clusters

```bash
# Create named clusters
./kind-cluster.sh create --name cluster1
./kind-cluster.sh create --name cluster2

# Deploy to specific cluster
./kind-cluster.sh deploy --name cluster1

# Switch contexts
kubectl config use-context kind-cluster1
```

### Observability Stack (Future)

```bash
# Deploy with full observability
./kind-cluster.sh deploy --with-observability

# Access Grafana dashboards
open http://localhost:3000

# Access Prometheus
open http://localhost:9080

# Access Jaeger traces
open http://localhost:16686
```

---

## Files and Directories

```
dev-tools/kind/
├── kind-cluster.sh           # Main CLI script
├── lib/
│   ├── logging.sh            # Logging and output functions
│   ├── config.sh             # Configuration loading
│   ├── prerequisites.sh      # Tool prerequisite checking
│   ├── cluster.sh            # Cluster lifecycle management
│   ├── deploy.sh             # Deployment functions
│   └── images.sh             # Image build/load functions
├── config/
│   ├── kind-config.yaml      # KinD cluster configuration
│   ├── helm-values.yaml      # Default Helm values
│   └── helm-values.local.yaml  # Local overrides (gitignored)
└── README.md                 # This file
```

---

## Exit Codes

- `0` - Success
- `1` - General error
- `2` - Prerequisites not met
- `3` - Cluster operation failed
- `4` - Deployment failed
- `5` - Unsafe operation blocked (e.g., wrong kubectl context)
- `130` - User cancelled operation

---

## Contributing

When modifying the tools:

1. Test all subcommands
2. Run `shellcheck` on modified scripts
3. Update this README if adding features
4. Follow existing code patterns and error handling

---

## Support

For issues or questions:
1. Check this README's Troubleshooting section
2. Review `specs/001-kind-cluster-tools/` documentation
3. Check cluster/pod logs: `./kind-cluster.sh logs`
4. Consult project maintainers

---

## License

Same as parent Structures project - see LICENSE.txt in repository root.

