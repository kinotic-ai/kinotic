# CLI Interface Contract: KinD Cluster Tools

**Feature**: 001-kind-cluster-tools  
**Phase**: 1 - Design & Contracts  
**Date**: 2025-11-26

## Overview

This document defines the command-line interface contract for the KinD cluster developer tooling. The interface follows standard CLI conventions with subcommands, flags, and environment variable support.

## Main Command

### Synopsis

```bash
kind-cluster.sh <subcommand> [options]
```

### Global Options

| Flag | Environment Variable | Description | Default |
|------|---------------------|-------------|---------|
| `--verbose, -v` | `VERBOSE=1` | Enable verbose logging | false |
| `--dry-run` | `DRY_RUN=1` | Print commands without executing | false |
| `--help, -h` | - | Show help message | - |

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | General error |
| 2 | Prerequisites not met |
| 3 | Cluster operation failed |
| 4 | Deployment failed |
| 5 | Unsafe operation blocked |

## Subcommands

### 1. create

Create a new KinD cluster for structures testing.

**Synopsis**:
```bash
kind-cluster.sh create [options]
```

**Options**:
| Flag | Environment Variable | Description | Default |
|------|---------------------|-------------|---------|
| `--name <name>` | `KIND_CLUSTER_NAME` | Cluster name | structures-cluster |
| `--config <path>` | `KIND_CONFIG_PATH` | Path to kind-config.yaml | config/kind-config.yaml |
| `--k8s-version <version>` | `K8S_VERSION` | Kubernetes version | latest |
| `--skip-checks` | `SKIP_CHECKS=1` | Skip prerequisite checks | false |
| `--force` | - | Recreate if cluster exists | false |

**Behavior**:
1. Check prerequisites (docker, kind, kubectl)
2. Check if cluster with same name exists
3. If exists and `--force` not set, exit with error
4. If exists and `--force` set, delete existing cluster first
5. Create cluster using KinD configuration
6. Switch kubectl context to new cluster
7. Wait for cluster to be ready
8. Display cluster info and connection details

**Output**:
```
✓ Checking prerequisites...
  - Docker: ✓ running
  - kind: ✓ v0.20.0
  - kubectl: ✓ v1.28.0
  
✓ Creating kind cluster 'structures-cluster'...
  - Control plane node: ✓ ready
  - Worker node 1: ✓ ready
  - Worker node 2: ✓ ready
  
✓ Cluster created successfully!

  Cluster Name: structures-cluster
  Kubernetes: v1.28.0
  API Server: https://127.0.0.1:6443
  Context: kind-structures-cluster
  
  Nodes: 3 (1 control-plane, 2 workers)
  Status: Ready
  
Next steps:
  - Deploy structures-server: ./kind-cluster.sh deploy
  - Check status: ./kind-cluster.sh status
```

**Exit Codes**:
- 0: Cluster created successfully
- 2: Prerequisites not met
- 3: Cluster creation failed
- 5: Cluster exists and --force not set

---

### 2. deploy

Deploy structures-server and dependencies to the KinD cluster.

**Synopsis**:
```bash
kind-cluster.sh deploy [options]
```

**Options**:
| Flag | Environment Variable | Description | Default |
|------|---------------------|-------------|---------|
| `--name <name>` | `KIND_CLUSTER_NAME` | Cluster name | structures-cluster |
| `--chart <path>` | `HELM_CHART_PATH` | Path to Helm chart | ./helm/structures |
| `--values <path>` | `HELM_VALUES_PATH` | Path to values file | config/helm-values.yaml |
| `--set <key=value>` | - | Override Helm values | - |
| `--with-deps` | `DEPLOY_DEPS=1` | Deploy dependencies (ES, Keycloak) | true |
| `--with-observability` | `DEPLOY_OBSERVABILITY=1` | Deploy observability stack (OTEL, Prometheus, Grafana) | false |
| `--wait-timeout <duration>` | `DEPLOY_TIMEOUT` | Deployment timeout | 5m |

**Behavior**:
1. Verify cluster exists and is accessible
2. If `--with-deps`, deploy Elasticsearch and Keycloak first
3. Deploy structures-server via Helm upgrade --install
4. Wait for all pods to be ready
5. Run health checks
6. Display deployment status and access information

**Output** (with --with-deps):
```
✓ Verifying cluster 'structures-cluster'...
  - Cluster: ✓ running
  - Context: ✓ kind-structures-cluster
  - Nodes: ✓ 3 ready

✓ Deploying dependencies...
  - Elasticsearch: ✓ deployed (3/3 pods ready)
  - Keycloak PostgreSQL: ✓ deployed (1/1 pods ready)
  - Keycloak: ✓ deployed (1/1 pods ready)
    - Test realm imported: ✓ test
    - OIDC client configured: ✓ structures-client

✓ Deploying structures-server...
  - Release: ✓ structures-server (revision 1)
  - Pods: ✓ 2/2 ready
  - Services: ✓ NodePort 30090
  - OIDC: ✓ configured (Keycloak)
  
✓ Deployment successful!

  Release: structures-server
  Revision: 1
  Status: deployed
  
  Access URLs:
  - Server: http://localhost:9090
  - Health: http://localhost:9090/health
  - Keycloak: http://localhost:8888/auth
  - Keycloak Admin: http://localhost:8888/auth/admin (admin/admin)
  
  Pods:
  - structures-server-7d4f8c8b9-abcde: Running (OIDC enabled)
  - structures-server-7d4f8c8b9-fghij: Running (OIDC enabled)
  - keycloak-0: Running
  - keycloak-db-postgresql-0: Running
  - elasticsearch-master-0: Running
  
Next steps:
  - Login to Structures: http://localhost:9090/login
  - Check logs: kubectl logs -f deployment/structures-server
  - Run tests: ./gradlew :structures-core:integrationTest
```

**Output** (with --with-deps --with-observability):
```
✓ Verifying cluster 'structures-cluster'...
  - Cluster: ✓ running
  - Context: ✓ kind-structures-cluster
  - Nodes: ✓ 3 ready

✓ Deploying dependencies...
  - Elasticsearch: ✓ deployed (3/3 pods ready)
  - Keycloak PostgreSQL: ✓ deployed (1/1 pods ready)
  - Keycloak: ✓ deployed (1/1 pods ready)

✓ Deploying observability stack...
  - OpenTelemetry Collector: ✓ deployed (1/1 pods ready)
  - Prometheus: ✓ deployed (1/1 pods ready)
  - Loki: ✓ deployed (1/1 pods ready)
  - Jaeger: ✓ deployed (1/1 pods ready)
  - Grafana: ✓ deployed (1/1 pods ready)
    - Dashboards imported: ✓ 3 dashboards

✓ Deploying structures-server...
  - Release: ✓ structures-server (revision 1)
  - Pods: ✓ 2/2 ready
  - Services: ✓ NodePort 30090
  - OIDC: ✓ configured (Keycloak)
  - Observability: ✓ enabled (OTEL endpoint configured)
  
✓ Deployment successful!

  Release: structures-server
  Revision: 1
  Status: deployed
  
  Access URLs:
  - Server: http://localhost:9090
  - Health: http://localhost:9090/health
  - Keycloak: http://localhost:8888/auth
  - Grafana: http://localhost:3000 (admin/admin)
  - Prometheus: http://localhost:9080
  - Jaeger: http://localhost:16686
  
  Pods:
  - structures-server-7d4f8c8b9-abcde: Running (OIDC + OTEL enabled)
  - structures-server-7d4f8c8b9-fghij: Running (OIDC + OTEL enabled)
  - keycloak-0: Running
  - elasticsearch-master-0: Running
  - otel-collector-xxx: Running
  - prometheus-server-xxx: Running
  - grafana-xxx: Running
  
Next steps:
  - Login to Structures: http://localhost:9090/login
  - View metrics: http://localhost:3000 (Grafana)
  - View traces: http://localhost:16686 (Jaeger)
  - Run tests: ./gradlew :structures-core:integrationTest
```

**Exit Codes**:
- 0: Deployment successful
- 3: Cluster not found or not accessible
- 4: Deployment failed

---

### 3. build

Build Docker image for structures-server using Gradle `bootBuildImage` task.

**Synopsis**:
```bash
kind-cluster.sh build [options]
```

**Options**:
| Flag | Environment Variable | Description | Default |
|------|---------------------|-------------|---------|
| `--module <name>` | - | Gradle module to build | structures-server |
| `--load` | - | Load image into cluster after build | false |

**Behavior**:
1. Run Gradle `bootBuildImage` task using standard build conventions
2. Image built with Spring Boot Cloud Native Buildpacks (Paketo)
3. Image name from gradle config: `kinotic/${module}:${version}`
4. Includes health checker and optimized JVM configuration
5. If `--load`, call `load` subcommand automatically
6. Display image information

**Output**:
```
✓ Building structures-server...
  - Gradle: running :structures-server:bootBuildImage
  - Buildpacks: ✓ paketo-buildpacks/java
  - Buildpacks: ✓ paketobuildpacks/health-checker
  - Image: ✓ built successfully
  
✓ Image built successfully!

  Image: kinotic/structures-server:0.5.0-SNAPSHOT
  Builder: paketocommunity/builder-ubi-base:latest
  Buildpacks: 
    - paketo-buildpacks/java
    - paketobuildpacks/health-checker
  Health Check: /health
  
Next steps:
  - Load into cluster: ./kind-cluster.sh load
  - Or rebuild with --load flag
```

**Exit Codes**:
- 0: Build successful
- 1: Gradle bootBuildImage failed

---

### 4. load

Load Docker image into KinD cluster nodes.

**Synopsis**:
```bash
kind-cluster.sh load [options]
```

**Options**:
| Flag | Environment Variable | Description | Default |
|------|---------------------|-------------|---------|
| `--name <name>` | `KIND_CLUSTER_NAME` | Cluster name | structures-cluster |
| `--image <name>` | - | Full image name | kinotic/structures-server:${version} |

**Behavior**:
1. Verify cluster exists
2. Determine image name from gradle.properties if not specified
3. Verify image exists locally (`docker images`)
4. Load image into all cluster nodes via `kind load docker-image`
5. Verify image available in cluster nodes via `crictl images`

**Output**:
```
✓ Loading image into cluster...
  - Cluster: structures-cluster
  - Image: kinotic/structures-server:0.5.0-SNAPSHOT
  - Nodes: 3
  
✓ Image loaded successfully!

  Image available on all cluster nodes
  Verified via: docker exec <node> crictl images
  
  To deploy with this image:
  ./kind-cluster.sh deploy --set image.repository=kinotic/structures-server \
                           --set image.tag=0.5.0-SNAPSHOT \
                           --set image.pullPolicy=Never
```

**Exit Codes**:
- 0: Image loaded successfully
- 1: Image not found locally (run ./kind-cluster.sh build first)
- 3: Cluster not found
- 3: Load failed

---

### 5. status

Display cluster and deployment status.

**Synopsis**:
```bash
kind-cluster.sh status [options]
```

**Options**:
| Flag | Environment Variable | Description | Default |
|------|---------------------|-------------|---------|
| `--name <name>` | `KIND_CLUSTER_NAME` | Cluster name | structures-cluster |
| `--watch, -w` | - | Watch status updates | false |

**Behavior**:
1. Check if cluster exists
2. Display cluster information (nodes, version, endpoints)
3. Display Helm releases
4. Display pod status
5. Display service endpoints
6. If `--watch`, refresh every 2 seconds

**Output** (with dependencies):
```
Cluster: structures-cluster
Status: ✓ Running
Context: kind-structures-cluster
API Server: https://127.0.0.1:6443

Nodes:
  ✓ structures-cluster-control-plane (control-plane) - Ready
  ✓ structures-cluster-worker (worker) - Ready
  ✓ structures-cluster-worker2 (worker) - Ready

Helm Releases:
  ✓ structures-server (deployed, revision 1)
  ✓ elasticsearch (deployed, revision 1)
  ✓ keycloak-db (deployed, revision 1)
  ✓ keycloak (deployed, revision 1)

Pods:
  ✓ structures-server-7d4f8c8b9-abcde - Running (0 restarts) [OIDC: enabled]
  ✓ structures-server-7d4f8c8b9-fghij - Running (0 restarts) [OIDC: enabled]
  ✓ elasticsearch-master-0 - Running (0 restarts)
  ✓ elasticsearch-master-1 - Running (0 restarts)
  ✓ keycloak-db-postgresql-0 - Running (0 restarts)
  ✓ keycloak-0 - Running (0 restarts)

Services:
  structures-server: NodePort 30090 → http://localhost:9090
  elasticsearch: ClusterIP (internal only)
  keycloak: NodePort 30888 → http://localhost:8888/auth
  keycloak-db: ClusterIP (internal only)

OIDC Configuration:
  ✓ Provider: Keycloak
  ✓ Realm: test
  ✓ Client: structures-client
  ✓ Authority: http://keycloak:8888/auth/realms/test
```

**Output** (with dependencies + observability):
```
Cluster: structures-cluster
Status: ✓ Running
Context: kind-structures-cluster
API Server: https://127.0.0.1:6443

Nodes:
  ✓ structures-cluster-control-plane (control-plane) - Ready
  ✓ structures-cluster-worker (worker) - Ready
  ✓ structures-cluster-worker2 (worker) - Ready

Helm Releases:
  ✓ structures-server (deployed, revision 1)
  ✓ elasticsearch (deployed, revision 1)
  ✓ keycloak-db (deployed, revision 1)
  ✓ keycloak (deployed, revision 1)
  ✓ otel-collector (deployed, revision 1)
  ✓ prometheus (deployed, revision 1)
  ✓ grafana (deployed, revision 1)
  ✓ loki (deployed, revision 1)

Pods:
  ✓ structures-server-7d4f8c8b9-abcde - Running (0 restarts) [OIDC: enabled, OTEL: enabled]
  ✓ structures-server-7d4f8c8b9-fghij - Running (0 restarts) [OIDC: enabled, OTEL: enabled]
  ✓ elasticsearch-master-0 - Running (0 restarts)
  ✓ keycloak-0 - Running (0 restarts)
  ✓ otel-collector-xxx - Running (0 restarts)
  ✓ prometheus-server-xxx - Running (0 restarts)
  ✓ grafana-xxx - Running (0 restarts)

Services:
  structures-server: NodePort 30090 → http://localhost:9090
  elasticsearch: ClusterIP (internal only)
  keycloak: NodePort 30888 → http://localhost:8888/auth
  grafana: NodePort 30300 → http://localhost:3000
  prometheus: NodePort 30080 → http://localhost:9080
  jaeger: NodePort 30686 → http://localhost:16686

OIDC Configuration:
  ✓ Provider: Keycloak
  ✓ Realm: test
  ✓ Client: structures-client

Observability:
  ✓ OpenTelemetry: http://otel-collector:4317 (gRPC), :4318 (HTTP)
  ✓ Metrics: Prometheus → Grafana
  ✓ Traces: Jaeger → Grafana
  ✓ Logs: Loki → Grafana
  ✓ Dashboards: 3 imported
```

**Exit Codes**:
- 0: Status retrieved successfully
- 3: Cluster not found

---

### 6. delete

Delete the KinD cluster and all resources.

**Synopsis**:
```bash
kind-cluster.sh delete [options]
```

**Options**:
| Flag | Environment Variable | Description | Default |
|------|---------------------|-------------|---------|
| `--name <name>` | `KIND_CLUSTER_NAME` | Cluster name | structures-cluster |
| `--force, -f` | - | Skip confirmation prompt | false |

**Behavior**:
1. Verify cluster exists
2. Check kubectl context (must be kind context)
3. If not `--force`, prompt for confirmation
4. Delete cluster via kind CLI
5. Verify cluster and containers removed

**Output**:
```
⚠️  About to delete cluster 'structures-cluster'
   This will remove all pods, deployments, and data.
   
   Continue? [y/N]: y
   
✓ Deleting cluster...
  - Stopping nodes: ✓ done
  - Removing containers: ✓ done
  - Cleaning up: ✓ done
  
✓ Cluster 'structures-cluster' deleted successfully!
```

**Exit Codes**:
- 0: Cluster deleted successfully
- 3: Cluster not found
- 5: Unsafe context (not a kind cluster)
- 130: User cancelled

---

### 7. logs

Display logs from structures-server pods.

**Synopsis**:
```bash
kind-cluster.sh logs [options]
```

**Options**:
| Flag | Environment Variable | Description | Default |
|------|---------------------|-------------|---------|
| `--name <name>` | `KIND_CLUSTER_NAME` | Cluster name | structures-cluster |
| `--follow, -f` | - | Follow log output | false |
| `--tail <n>` | - | Show last N lines | 100 |
| `--all-pods` | - | Show logs from all pods | false |

**Behavior**:
1. Verify cluster and deployment exist
2. Get pod names for structures-server
3. Stream logs via kubectl
4. If `--all-pods`, multiplex logs from all pods

**Output**:
```
Following logs from structures-server-7d4f8c8b9-abcde...
---
2025-11-26 10:15:23.456 INFO  [main] o.k.s.StructuresServerApplication - Starting StructuresServerApplication...
2025-11-26 10:15:24.123 INFO  [main] o.k.s.config.ElasticsearchConfig - Connecting to Elasticsearch at elasticsearch:9200
2025-11-26 10:15:24.789 INFO  [main] o.k.s.StructuresServerApplication - Started StructuresServerApplication in 2.456 seconds
```

**Exit Codes**:
- 0: Logs displayed successfully
- 3: Cluster or deployment not found

## Environment Variables

All subcommands respect these environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `KIND_CLUSTER_NAME` | Default cluster name | structures-cluster |
| `KIND_CONFIG_PATH` | Default KinD config path | config/kind-config.yaml |
| `HELM_CHART_PATH` | Default Helm chart path | ./helm/structures |
| `HELM_VALUES_PATH` | Default Helm values path | config/helm-values.yaml |
| `IMAGE_NAME` | Full image name | kinotic/structures-server |
| `IMAGE_VERSION` | Image version | from gradle.properties |
| `VERBOSE` | Enable verbose output | 0 |
| `DRY_RUN` | Dry run mode | 0 |
| `SKIP_CHECKS` | Skip prerequisite checks | 0 |
| `DEPLOY_DEPS` | Deploy dependencies | 1 |
| `DEPLOY_OBSERVABILITY` | Deploy observability stack | 0 |

## Configuration Files

Configuration file locations (in priority order):

1. Command-line `--config` flag
2. `KIND_CONFIG_PATH` environment variable
3. `./dev-tools/kind/config/kind-config.yaml` (default)

Helm values locations (in priority order):

1. Command-line `--values` flag
2. `HELM_VALUES_PATH` environment variable
3. `./dev-tools/kind/config/helm-values.yaml` (default)
4. `./dev-tools/kind/config/helm-values.local.yaml` (user overrides, gitignored)

## Examples

### Create cluster with custom name
```bash
./kind-cluster.sh create --name test-cluster
```

### Deploy with custom values
```bash
./kind-cluster.sh deploy --values my-values.yaml
```

### Build and load image
```bash
./kind-cluster.sh build --load --tag my-feature
```

### Deploy with inline value override
```bash
./kind-cluster.sh deploy --set replicaCount=3
```

### Deploy with observability stack
```bash
./kind-cluster.sh deploy --with-observability
```

### Deploy without dependencies (structures-server only)
```bash
# Assumes dependencies already deployed
DEPLOY_DEPS=0 ./kind-cluster.sh deploy
```

### Delete without confirmation
```bash
./kind-cluster.sh delete --force
```

### Environment variable configuration
```bash
export KIND_CLUSTER_NAME=my-cluster
export VERBOSE=1
./kind-cluster.sh create
./kind-cluster.sh deploy
./kind-cluster.sh status
```

## Error Handling

All errors include:
1. Clear error message describing what failed
2. Context about what was being attempted
3. Suggestions for remediation
4. Appropriate exit code

Example error output:
```
ERROR: Docker daemon not running

Details:
  Command 'docker ps' failed with exit code 1
  
  The Docker daemon must be running to create KinD clusters.
  
Remediation:
  - Start Docker Desktop (macOS/Windows)
  - Start Docker service: sudo systemctl start docker (Linux)
  - Verify Docker is running: docker ps
  
Exit code: 2
```

## Notes

- All commands are idempotent where possible (create with --force, deploy via upgrade --install)
- Context safety checks prevent accidental operations on production clusters
- Verbose mode (`--verbose` or `-v`) shows all executed commands
- Dry run mode (`--dry-run`) prints commands without executing them
- All kubectl/helm/kind commands use absolute paths to avoid ambiguity

