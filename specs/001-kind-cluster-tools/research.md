# Research: KinD Cluster Developer Tools

**Feature**: 001-kind-cluster-tools  
**Phase**: 0 - Outline & Research  
**Date**: 2025-11-26

## Purpose

Document research findings and design decisions for the KinD cluster developer tooling, resolving technical unknowns and establishing implementation patterns.

## Research Tasks

### 1. KinD Configuration Best Practices

**Decision**: Use KinD configuration files with sensible defaults for local development clusters

**Rationale**:
- KinD supports YAML configuration for cluster topology, networking, and feature gates
- Multi-node clusters (1 control-plane + 2 workers) provide realistic testing environment
- Port mappings allow direct access to services without ingress controllers
- Extra mounts enable sharing local files with cluster nodes

**Alternatives Considered**:
- Single-node clusters: Simpler but don't test multi-node coordination
- Minikube: Alternative local Kubernetes but KinD is faster and uses Docker directly
- k3d: Similar to KinD but KinD has better kubectl integration

**Configuration Pattern**:
```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  extraPortMappings:
  - containerPort: 30080
    hostPort: 8080
- role: worker
- role: worker
```

### 2. Helm Deployment Strategy

**Decision**: Use `helm upgrade --install` for idempotent deployments with custom values files

**Rationale**:
- `helm upgrade --install` is idempotent - can be run multiple times safely
- Custom values files allow environment-specific configuration without modifying charts
- `--wait` flag ensures deployment completes before returning
- `--atomic` flag rolls back on failure automatically

**Alternatives Considered**:
- Separate `helm install` and `helm upgrade` commands: Requires state checking logic
- kubectl apply: Doesn't leverage Helm's templating and release management
- kustomize: Adds complexity, Helm already used in project

**Implementation Pattern**:
```bash
helm upgrade --install structures-server ./helm/structures \
  --values config/helm-values.yaml \
  --wait --timeout 5m --atomic
```

### 3. Prerequisite Detection

**Decision**: Check for required tools (docker, kind, kubectl, helm) and provide installation guidance

**Rationale**:
- Clear error messages prevent confusing failure modes
- `command -v` is portable across unix/linux shells
- Provide OS-specific installation instructions (homebrew for macOS, apt/yum for Linux)
- Version checking ensures compatibility

**Alternatives Considered**:
- Auto-install tools: Too invasive, users should control installations
- Assume all tools present: Poor user experience when tools missing
- Docker-in-Docker for KinD: Adds unnecessary complexity

**Implementation Pattern**:
```bash
check_prerequisite() {
  local tool=$1
  if ! command -v "$tool" &> /dev/null; then
    echo "ERROR: $tool not found"
    echo "Install: ..."
    exit 1
  fi
}
```

### 4. Image Build and Load Strategy

**Decision**: Use existing Gradle `bootBuildImage` task from build conventions, with conditional publish for CI/CD

**Rationale**:
- Standardized build tooling already configured in `buildSrc/src/main/groovy/org.kinotic.java-application-conventions.gradle`
- `bootBuildImage` uses Spring Boot's Cloud Native Buildpacks (Paketo) for optimized images
- Includes health checker buildpack and proper JVM configuration
- Currently `publish = false` is static (line 37) - should be conditional on CI environment
- `kind load docker-image` loads images directly into cluster nodes without registry
- Image tags include version from gradle.properties

**Alternatives Considered**:
- Manual docker build with Dockerfile: Doesn't match CI/CD pipeline tooling
- Push to local registry: Requires registry setup, KinD load is simpler
- Build inside cluster: Slower, requires build tools in cluster
- Use existing CI images: Doesn't support testing local changes

**Implementation Pattern**:
```bash
# Build image using standard tooling
./gradlew :structures-server:bootBuildImage

# Image name from gradle config: kinotic/structures-server:${version}
IMAGE_NAME="kinotic/structures-server:$(grep '^structuresVersion=' gradle.properties | cut -d'=' -f2)"

# Load into KinD cluster
kind load docker-image "$IMAGE_NAME" --name structures-cluster

# Verify image loaded
docker exec structures-cluster-control-plane crictl images | grep structures-server
```

**Gradle Configuration Update Needed**:

The `publish` flag in `bootBuildImage` configuration should be conditional on CI environment:

```groovy
// In buildSrc/src/main/groovy/org.kinotic.java-application-conventions.gradle
bootBuildImage {
    network = "host"
    publish = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"  // Only publish in CI/CD
    imageName = "kinotic/${project.name}:${project.version}"
    // ... rest of configuration
}
```

This ensures:
- Local builds: `publish = false` (no registry push)
- CI/CD builds: `publish = true` (push to Docker Hub)
- Matches existing docker registry configuration using DOCKER_HUB_USERNAME/PASSWORD env vars

### 5. Cluster Lifecycle Management

**Decision**: Create named clusters with state tracking via kubectl context and kind CLI

**Rationale**:
- Named clusters (`--name structures-cluster`) allow multiple KinD clusters
- kubectl context automatically switched by `kind create cluster`
- `kind get clusters` lists existing clusters for state checking
- `kind delete cluster` cleanly removes all resources

**Alternatives Considered**:
- Generate random cluster names: Harder to reference and manage
- Single global cluster: Doesn't support multiple test environments
- External state file: Kind CLI already tracks cluster state

**Implementation Pattern**:
```bash
CLUSTER_NAME="${KIND_CLUSTER_NAME:-structures-cluster}"
kind create cluster --name "$CLUSTER_NAME" --config config/kind-config.yaml
```

### 6. Dependency Deployment (Elasticsearch, Keycloak)

**Decision**: Use Bitnami Helm charts configured to match docker-compose setup exactly

**Rationale**:
- Keycloak MUST match `docker-compose/compose.keycloak.yml` configuration
  - Keycloak 26.3.3 with PostgreSQL 15 backend
  - Import test realm from `docker-compose/keycloak-test-realm.json`
  - Start in dev mode with metrics and health endpoints enabled
  - Port 8888 with `/auth` relative path
  - Admin credentials: admin/admin
- Elasticsearch from Bitnami chart with defaults suitable for testing
- ConfigMaps for Keycloak realm import
- OIDC provider configuration in structures-server Helm values

**Alternatives Considered**:
- Generic Bitnami charts without customization: Won't match docker-compose behavior
- Deploy from docker-compose: Doesn't integrate well with Kubernetes
- Skip dependencies: Structures-server won't work without them
- Different Keycloak version: Must match docker-compose for consistency

**Implementation Pattern**:
```bash
# Create ConfigMap with test realm
kubectl create configmap keycloak-realm \
  --from-file=test-realm.json=docker-compose/keycloak-test-realm.json

# Deploy PostgreSQL for Keycloak
helm upgrade --install keycloak-db bitnami/postgresql \
  --set auth.database=keycloak \
  --set auth.username=keycloak \
  --set auth.password=keycloak \
  --wait

# Deploy Keycloak with custom values
helm upgrade --install keycloak bitnami/keycloak \
  --set image.tag=26.3.3 \
  --set auth.adminUser=admin \
  --set auth.adminPassword=admin \
  --set postgresql.enabled=false \
  --set externalDatabase.host=keycloak-db-postgresql \
  --set extraEnvVars[0].name=KC_HTTP_RELATIVE_PATH \
  --set extraEnvVars[0].value=/auth \
  --set service.type=NodePort \
  --set service.nodePorts.http=30888 \
  --wait

# Deploy Elasticsearch
helm upgrade --install elasticsearch bitnami/elasticsearch --wait
```

### 7. Error Handling and Safety Checks

**Decision**: Implement context verification, interactive prompts for destructive operations, comprehensive error checking

**Rationale**:
- Verify kubectl context before delete to prevent production accidents
- Prompt confirmation for destructive operations (delete, recreate)
- Check Docker daemon running before attempting cluster operations
- Validate KinD cluster health after creation

**Alternatives Considered**:
- Force flags to skip prompts: Easy to accidentally destroy things
- No safety checks: Too dangerous for automation scripts
- Read-only mode: Limits utility of tooling

**Implementation Pattern**:
```bash
verify_safe_context() {
  local current_context=$(kubectl config current-context)
  if [[ ! "$current_context" == *"kind"* ]]; then
    echo "ERROR: Current context '$current_context' doesn't look like a kind cluster"
    echo "Refusing to proceed. Switch to kind context first."
    exit 1
  fi
}
```

### 8. CLI Interface Design

**Decision**: Single main script with subcommands: `create`, `deploy`, `build`, `load`, `delete`, `status`

**Rationale**:
- Subcommand pattern familiar to developers (like git, docker, kubectl)
- Single entry point easier to document and discover
- Consistent flag conventions across commands
- Help text for each subcommand

**Alternatives Considered**:
- Separate script per command: Harder to maintain, code duplication
- Interactive menu: Less scriptable, slower for experienced users
- Makefile targets: Works but Bash provides better error handling

**Implementation Pattern**:
```bash
# ./dev-tools/kind/kind-cluster.sh create
# ./dev-tools/kind/kind-cluster.sh deploy
# ./dev-tools/kind/kind-cluster.sh delete
```

### 9. Observability Stack (Optional)

**Decision**: Deploy OpenTelemetry, Prometheus, Grafana stack matching docker-compose observability setup

**Rationale**:
- Structures framework includes OpenTelemetry integration (Principle XI: Observability)
- docker-compose has comprehensive observability: `compose-otel.yml`
- Optional deployment via `--with-observability` flag
- Provides metrics, traces, and logs for cluster testing
- Grafana dashboards pre-configured from `docker-compose/grafana-dashboards/`
- Matches production-like observability environment

**Alternatives Considered**:
- Skip observability: Misses important testing capability
- Use different tools: Should match docker-compose for consistency
- Always deploy: Adds complexity for simple testing scenarios

**Implementation Pattern**:
```bash
# Deploy OpenTelemetry Collector
helm upgrade --install otel-collector open-telemetry/opentelemetry-collector \
  --set mode=deployment \
  --set service.type=NodePort \
  --wait

# Deploy Jaeger (traces)
kubectl apply -f https://github.com/jaegertracing/jaeger-operator/releases/download/v1.51.0/jaeger-operator.yaml
kubectl apply -f - <<EOF
apiVersion: jaegertracing.io/v1
kind: Jaeger
metadata:
  name: jaeger
spec:
  strategy: allInOne
EOF

# Deploy Prometheus (metrics)
helm upgrade --install prometheus prometheus-community/prometheus \
  --set server.service.type=NodePort \
  --set server.service.nodePort=30080 \
  --wait

# Deploy Loki (logs) 
helm upgrade --install loki grafana/loki-stack --wait

# Deploy Grafana (visualization)
helm upgrade --install grafana grafana/grafana \
  --set service.type=NodePort \
  --set service.nodePort=30300 \
  --set adminPassword=admin \
  --wait

# Import dashboards from docker-compose/grafana-dashboards/
kubectl create configmap grafana-dashboards \
  --from-file=docker-compose/grafana-dashboards/
```

**Configuration Integration**:
- OpenTelemetry endpoint: http://otel-collector:4317 (gRPC)
- Prometheus scrapes from OTEL collector on port 8889
- Grafana pre-configured with Prometheus, Loki, Jaeger datasources
- Structures-server environment variables set to export to OTEL

## Summary

Implementation will use:
- **Bash scripts** (4.0+) with modular library functions
- **KinD configuration files** for reproducible cluster topology
- **Helm upgrade --install** for idempotent deployments
- **kind load docker-image** for local image testing
- **Subcommand CLI** interface (create/deploy/build/load/delete/status)
- **Safety checks** for context verification and destructive operations
- **Prerequisite validation** with helpful error messages
- **Keycloak configuration** matching docker-compose (PostgreSQL backend, test realm import, port 8888)
- **Optional observability stack** (OTEL, Prometheus, Grafana, Jaeger, Loki)

All decisions align with Structures constitution principles: convention-over-configuration, MVP focus, integration-first testing, security-first approach, and observability.

