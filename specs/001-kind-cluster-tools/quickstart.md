# Quickstart: KinD Cluster Developer Tools

**Feature**: 001-kind-cluster-tools  
**Phase**: 1 - Design & Contracts  
**Date**: 2025-11-26

## Overview

This quickstart guide walks you through setting up a local Kubernetes environment using KinD for testing cluster coordination features in structures-server.

## Prerequisites

Before you begin, ensure you have:

1. **Docker** (Docker Desktop for macOS/Windows, or Docker Engine for Linux)
   - Version 20.10 or higher
   - Running and accessible

2. **Git** (for cloning the repository if needed)

3. **Sufficient Resources**
   - Minimum: 8GB RAM, 4 CPU cores
   - Recommended: 16GB RAM, 8 CPU cores

## Installation

The tool will automatically download and install required CLIs (kind, kubectl, helm) if not present.

### Clone the Repository

If you haven't already:

```bash
git clone https://github.com/MindIgnited/structures.git
cd structures
```

### Verify Docker

Ensure Docker is running:

```bash
docker ps
```

If this fails, start Docker Desktop or the Docker daemon.

## Quick Start (5 Minutes)

### 1. Create the Cluster

Create a KinD cluster with default settings:

```bash
./dev-tools/kind/kind-cluster.sh create
```

Expected output:
```
✓ Checking prerequisites...
✓ Creating kind cluster 'structures-cluster'...
✓ Cluster created successfully!
```

This creates a 3-node cluster (1 control-plane + 2 workers).

### 2. Deploy structures-server

Deploy structures-server with dependencies (Elasticsearch + Keycloak):

```bash
./dev-tools/kind/kind-cluster.sh deploy
```

Expected output:
```
✓ Deploying dependencies...
  - Elasticsearch: ✓ deployed
  - Keycloak (with PostgreSQL): ✓ deployed
  - Test realm imported: ✓
✓ Deploying structures-server...
  - OIDC configured: ✓ Keycloak
✓ Deployment successful!

Access URLs:
- Server: http://localhost:9090
- Keycloak: http://localhost:8888/auth (admin/admin)
```

**With Observability Stack** (optional):

For testing with full observability (metrics, traces, logs):

```bash
./dev-tools/kind/kind-cluster.sh deploy --with-observability
```

This adds:
- OpenTelemetry Collector (traces, metrics, logs)
- Prometheus (metrics storage)
- Grafana (visualization) at http://localhost:3000
- Jaeger (tracing UI) at http://localhost:16686

### 3. Verify Deployment

Check that everything is running:

```bash
./dev-tools/kind/kind-cluster.sh status
```

Expected output:
```
Cluster: structures-cluster
Status: ✓ Running

Pods:
  ✓ structures-server-xxx - Running
  ✓ elasticsearch-master-0 - Running
```

### 4. Access structures-server

Open your browser to:
- **Server UI**: http://localhost:9090
- **Health Check**: http://localhost:9090/health
- **Keycloak Admin**: http://localhost:8888/auth/admin
  - Username: `admin`
  - Password: `admin`
- **Keycloak Test Realm**: http://localhost:8888/auth/realms/test

**If deployed with --with-observability**:
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9080
- **Jaeger Tracing**: http://localhost:16686

## Common Workflows

### Testing Local Changes

When you make code changes to structures-server:

```bash
# 1. Build new image using standard Gradle tooling
./dev-tools/kind/kind-cluster.sh build --load

# This runs: ./gradlew :structures-server:bootBuildImage
# Image built: mindignited/structures-server:${version}
# Automatically loaded into KinD cluster

# 2. Redeploy with new image
./dev-tools/kind/kind-cluster.sh deploy \
  --set image.repository=mindignited/structures-server \
  --set image.tag=0.5.0-SNAPSHOT \
  --set image.pullPolicy=Never

# 3. Verify new version running
./dev-tools/kind/kind-cluster.sh status
```

**Note**: The `build` command uses the same `bootBuildImage` task as CI/CD pipelines, ensuring consistency between local development and production builds.

### Viewing Logs

Follow structures-server logs:

```bash
./dev-tools/kind/kind-cluster.sh logs --follow
```

Or use kubectl directly:

```bash
kubectl logs -f deployment/structures-server
```

### Running Integration Tests

With the cluster running, execute integration tests:

```bash
./gradlew :structures-core:integrationTest
```

Tests will run against http://localhost:9090.

### Cluster Cleanup

When done testing, delete the cluster to free resources:

```bash
./dev-tools/kind/kind-cluster.sh delete
```

Confirm deletion when prompted, or use `--force` to skip confirmation:

```bash
./dev-tools/kind/kind-cluster.sh delete --force
```

## Configuration

### Custom Cluster Settings

Create a custom KinD configuration:

```bash
# Copy default config
cp dev-tools/kind/config/kind-config.yaml my-config.yaml

# Edit as needed
vim my-config.yaml

# Create cluster with custom config
./dev-tools/kind/kind-cluster.sh create --config my-config.yaml
```

### Custom Helm Values

Create custom Helm values for structures-server:

```bash
# Copy default values
cp dev-tools/kind/config/helm-values.yaml my-values.yaml

# Edit as needed
vim my-values.yaml

# Deploy with custom values
./dev-tools/kind/kind-cluster.sh deploy --values my-values.yaml
```

Or use inline overrides:

```bash
./dev-tools/kind/kind-cluster.sh deploy \
  --set replicaCount=3 \
  --set image.tag=custom-tag
```

### Environment Variables

Set defaults via environment variables:

```bash
export KIND_CLUSTER_NAME=my-test-cluster
export VERBOSE=1

./dev-tools/kind/kind-cluster.sh create
./dev-tools/kind/kind-cluster.sh deploy
```

## Troubleshooting

### Prerequisites Not Met

If you see errors about missing tools:

```bash
# macOS (Homebrew)
brew install kind kubectl helm

# Linux (apt)
curl -Lo ./kind https://kind.sigs.k8s.io/dl/latest/kind-linux-amd64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind
```

The tool will provide OS-specific installation instructions.

### Cluster Creation Fails

**Port conflicts**: If ports 8080, 9090, or 6443 are in use:

```bash
# Find what's using the port
lsof -i :9090

# Kill the process or use a different port in config
```

**Insufficient resources**: Ensure Docker has enough memory:
- Docker Desktop: Settings → Resources → Memory (set to at least 8GB)

### Deployment Fails

**Pods not starting**:

```bash
# Check pod status
kubectl get pods

# Check pod logs
kubectl logs <pod-name>

# Describe pod for events
kubectl describe pod <pod-name>
```

**Image pull errors**: If using custom images, ensure they're loaded:

```bash
./dev-tools/kind/kind-cluster.sh load --tag your-tag
```

### Cluster Not Accessible

**Context issues**:

```bash
# Verify context
kubectl config current-context

# Should show: kind-structures-cluster

# Switch if needed
kubectl config use-context kind-structures-cluster
```

### Keycloak Issues

**Keycloak not accessible**:

```bash
# Check Keycloak pod status
kubectl get pods -l app.kubernetes.io/name=keycloak

# Check Keycloak logs
kubectl logs -l app.kubernetes.io/name=keycloak --tail=100

# Verify realm imported
kubectl exec -it keycloak-0 -- \
  /opt/keycloak/bin/kcadm.sh get realms/test
```

**OIDC authentication failing**:

```bash
# Verify structures-server OIDC configuration
kubectl get configmap structures-server-config -o yaml

# Check structures-server logs for OIDC errors
kubectl logs -l app=structures-server | grep -i oidc
```

### Observability Issues

**Grafana not showing data**:

```bash
# Verify datasources configured
kubectl exec -it grafana-xxx -- \
  cat /etc/grafana/provisioning/datasources/datasource.yaml

# Check Prometheus scraping
curl http://localhost:9080/api/v1/targets

# Check OTEL collector receiving data
kubectl logs -l app.kubernetes.io/name=otel-collector
```

**Traces not appearing in Jaeger**:

```bash
# Verify structures-server sending traces
kubectl logs -l app=structures-server | grep -i otel

# Check Jaeger collector
kubectl logs -l app=jaeger
```

### Complete Reset

If things are broken, delete and recreate:

```bash
./dev-tools/kind/kind-cluster.sh delete --force
./dev-tools/kind/kind-cluster.sh create
./dev-tools/kind/kind-cluster.sh deploy --with-observability
```

## Advanced Usage

### Multiple Clusters

Create multiple clusters for different purposes:

```bash
# Create dev cluster
./dev-tools/kind/kind-cluster.sh create --name dev-cluster

# Create test cluster
./dev-tools/kind/kind-cluster.sh create --name test-cluster

# Deploy to specific cluster
./dev-tools/kind/kind-cluster.sh deploy --name dev-cluster

# Check status of specific cluster
./dev-tools/kind/kind-cluster.sh status --name test-cluster
```

### Custom Kubernetes Version

Test against specific Kubernetes versions:

```bash
./dev-tools/kind/kind-cluster.sh create --k8s-version v1.28.0
```

### Watch Mode

Monitor cluster status in real-time:

```bash
./dev-tools/kind/kind-cluster.sh status --watch
```

Press Ctrl+C to stop watching.

### Verbose Logging

See all commands executed:

```bash
./dev-tools/kind/kind-cluster.sh create --verbose
```

Or:

```bash
export VERBOSE=1
./dev-tools/kind/kind-cluster.sh create
```

### Dry Run

Preview commands without executing:

```bash
./dev-tools/kind/kind-cluster.sh create --dry-run
```

## Next Steps

- **Run Integration Tests**: `./gradlew :structures-core:integrationTest`
- **Explore Kubernetes**: `kubectl get all --all-namespaces`
- **Check Helm Releases**: `helm list`
- **View Cluster Info**: `kubectl cluster-info`
- **Access Elasticsearch**: Port-forward to test directly
  ```bash
  kubectl port-forward svc/elasticsearch 9200:9200
  ```

## Getting Help

### Command Help

Get help for any command:

```bash
./dev-tools/kind/kind-cluster.sh --help
./dev-tools/kind/kind-cluster.sh create --help
./dev-tools/kind/kind-cluster.sh deploy --help
```

### Check Tool Versions

```bash
docker --version
kind --version
kubectl version --client
helm version
```

### Community Resources

- **KinD Documentation**: https://kind.sigs.k8s.io/
- **Kubectl Reference**: https://kubernetes.io/docs/reference/kubectl/
- **Helm Documentation**: https://helm.sh/docs/
- **Structures Project**: https://github.com/MindIgnited/structures

## Example Session

Complete workflow from start to finish:

```bash
# 1. Create cluster
./dev-tools/kind/kind-cluster.sh create
# ✓ Cluster created in 2 minutes

# 2. Deploy with dependencies and observability
./dev-tools/kind/kind-cluster.sh deploy --with-observability
# ✓ Elasticsearch deployed
# ✓ Keycloak deployed (with PostgreSQL)
# ✓ Observability stack deployed (OTEL, Prometheus, Grafana, Jaeger)
# ✓ Structures-server deployed with OIDC + OTEL
# ✓ Deployment complete in 5 minutes

# 3. Verify everything running
./dev-tools/kind/kind-cluster.sh status
# ✓ All pods running
# ✓ OIDC: Keycloak configured
# ✓ Observability: All services running

# 4. Access services
# - Structures: http://localhost:9090
# - Keycloak: http://localhost:8888/auth
# - Grafana: http://localhost:3000
# - Jaeger: http://localhost:16686

# 5. Make code changes
vim structures-core/src/main/java/MyClass.java

# 6. Test changes
./dev-tools/kind/kind-cluster.sh build --load
# Uses bootBuildImage task: mindignited/structures-server:0.5.0-SNAPSHOT
./dev-tools/kind/kind-cluster.sh deploy \
  --set image.repository=mindignited/structures-server \
  --set image.tag=0.5.0-SNAPSHOT \
  --set image.pullPolicy=Never

# 7. Run integration tests (with OIDC auth)
./gradlew :structures-core:integrationTest
# ✓ All tests passing

# 8. View traces and metrics in Grafana
# Open http://localhost:3000 and explore dashboards

# 9. Clean up
./dev-tools/kind/kind-cluster.sh delete --force
# ✓ Cluster deleted
```

Total time: ~12 minutes from zero to fully tested with observability.

**Basic Session (without observability)**:
```bash
./dev-tools/kind/kind-cluster.sh create
./dev-tools/kind/kind-cluster.sh deploy
# ✓ Ready in ~5 minutes with Keycloak OIDC
```

## Tips

- **Use tab completion**: Most shells support tab completion for kubectl and helm
- **Alias commands**: Create shell aliases for frequently used commands
- **Save custom configs**: Keep your custom configs in version control (except local overrides)
- **Monitor resources**: Use `kubectl top nodes` to monitor resource usage
- **Clean up regularly**: Delete clusters when not in use to free resources

Happy testing! 🚀

