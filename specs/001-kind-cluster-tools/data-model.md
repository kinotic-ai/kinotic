# Data Model: KinD Cluster Developer Tools

**Feature**: 001-kind-cluster-tools  
**Phase**: 1 - Design & Contracts  
**Date**: 2025-11-26

## Overview

This document defines the configuration structures and state models for the KinD cluster developer tooling. Since this is CLI tooling rather than an application with persistent data, the "data model" consists of configuration files, environment variables, and runtime state.

## Configuration Entities

### 1. Cluster Configuration

Represents the KinD cluster topology and settings.

**Format**: YAML file (`config/kind-config.yaml`)

**Attributes**:
- `apiVersion`: KinD API version (kind.x-k8s.io/v1alpha4)
- `kind`: Resource type (Cluster)
- `name`: Cluster identifier (optional, defaults to "structures-cluster")
- `nodes`: Array of node specifications
  - `role`: control-plane or worker
  - `image`: Kubernetes version image (optional)
  - `extraPortMappings`: Port mappings from host to container
  - `extraMounts`: Volume mounts from host to container
- `networking`: Cluster networking configuration
  - `apiServerPort`: API server port on host
  - `podSubnet`: Pod network CIDR
  - `serviceSubnet`: Service network CIDR

**Default Values**:
```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30080  # NodePort for structures-server
        hostPort: 8080
        protocol: TCP
      - containerPort: 30090  # NodePort for structures-server management
        hostPort: 9090
        protocol: TCP
  - role: worker
  - role: worker
networking:
  apiServerPort: 6443
```

**Validation Rules**:
- Must have exactly one control-plane node
- Worker nodes optional (0-N)
- Port mappings must not conflict with host ports
- Image must be valid kindest/node tag if specified

### 2. Helm Deployment Configuration

Represents structures-server deployment settings.

**Format**: YAML file (`config/helm-values.yaml`)

**Attributes**:
- `replicaCount`: Number of structures-server pods
- `image`: Container image settings
  - `repository`: Image repository
  - `tag`: Image tag
  - `pullPolicy`: Always, IfNotPresent, or Never
- `service`: Kubernetes service configuration
  - `type`: NodePort, ClusterIP, or LoadBalancer
  - `port`: Service port
  - `nodePort`: Fixed NodePort (for NodePort type)
- `elasticsearch`: Elasticsearch connection settings
  - `host`: Elasticsearch service name
  - `port`: Elasticsearch port
- `oidc`: OIDC authentication settings
  - `enabled`: Enable OIDC
  - `providers`: Array of OIDC provider configurations

**Default Values**:
```yaml
replicaCount: 2
image:
  repository: mindignited/structures-server  # Matches bootBuildImage output
  tag: 0.5.0-SNAPSHOT  # From gradle.properties version
  pullPolicy: Never  # Don't pull, use loaded images from KinD
service:
  type: NodePort
  port: 9090
  nodePort: 30090
elasticsearch:
  host: elasticsearch-master
  port: 9200
oidc:
  enabled: true  # Enable OIDC with Keycloak
  securityService:
    enabled: true
    debug: true
    tenantIdFieldName: tenantId
    frontendConfigurationPath: /app-config.override.json
    providers:
      - provider: keycloak
        displayName: Keycloak
        enabled: true
        rolesClaimPath: realm_access.roles
        domains:
          - example.com
        roles:
          - admin
        audience: structures-client
        clientId: structures-client
        authority: http://keycloak:8888/auth/realms/test
        redirectUri: http://localhost:9090/login
        postLogoutRedirectUri: http://localhost:9090
        silentRedirectUri: http://localhost:9090/login/silent-renew
openApi:
  securityType: BEARER  # Use bearer tokens from OIDC
logging:
  level:
    org.mindignited.structures.auth: TRACE
    org.mindignited.structures: TRACE
    io.vertx: TRACE
observability:
  otel:
    enabled: false  # Set to true when --with-observability used
    endpoint: http://otel-collector:4317
    serviceName: structures-server
```

**Validation Rules**:
- replicaCount must be >= 1
- pullPolicy must be Never for local images or IfNotPresent/Always for registry images
- Service type must be valid Kubernetes service type
- NodePort must be in range 30000-32767 if specified

### 3. Keycloak Configuration

Represents Keycloak OIDC provider setup matching docker-compose.

**Format**: Helm values + ConfigMap for realm import

**Attributes**:
- `image.tag`: Keycloak version (26.3.3 to match docker-compose)
- `auth.adminUser`: Admin username (admin)
- `auth.adminPassword`: Admin password (admin)
- `postgresql`: PostgreSQL backend configuration
  - `host`: keycloak-db-postgresql
  - `database`: keycloak
  - `username`: keycloak
  - `password`: keycloak
- `extraEnvVars`: Environment variables
  - `KC_HTTP_RELATIVE_PATH`: /auth (matches docker-compose)
  - `KC_METRICS_ENABLED`: true
  - `KC_HEALTH_ENABLED`: true
- `service.type`: NodePort
- `service.nodePorts.http`: 30888 (maps to host:8888)
- `realmImport`: Test realm JSON from docker-compose/keycloak-test-realm.json

**Default Values**:
- Matches `docker-compose/compose.keycloak.yml` exactly
- Test realm with structures-client pre-configured
- Realm access roles: admin
- Token lifetime: 300s (5 minutes)

**Validation Rules**:
- Realm JSON must be valid Keycloak import format
- PostgreSQL must be deployed and healthy before Keycloak
- HTTP relative path must match structures-server OIDC authority

### 4. Observability Stack Configuration

Represents optional monitoring and tracing setup.

**Format**: Multiple Helm releases

**Attributes**:
- `enabled`: Whether to deploy observability stack (default: false)
- `otelCollector`: OpenTelemetry Collector configuration
  - `endpoint`: gRPC endpoint (4317) and HTTP endpoint (4318)
  - `exporters`: Jaeger, Prometheus, Loki
- `prometheus`: Metrics storage and query
  - `nodePort`: 30080 (maps to host:9080)
  - `scrapeConfigs`: Scrape OTEL collector on port 8889
- `grafana`: Unified visualization
  - `nodePort`: 30300 (maps to host:3000)
  - `datasources`: Prometheus, Loki, Jaeger
  - `dashboards`: Import from docker-compose/grafana-dashboards/
- `jaeger`: Distributed tracing
  - `uiPort`: 16686
  - `strategy`: allInOne
- `loki`: Log aggregation
  - `port`: 3100

**Default Values**:
- Matches `docker-compose/compose-otel.yml` configuration
- Grafana anonymous auth enabled (admin role)
- Pre-configured datasources for all backends
- Structures dashboards from docker-compose folder

**Validation Rules**:
- All components must be healthy before marking observability ready
- OTEL collector must be reachable from structures-server pods
- Grafana dashboards must be valid JSON

### 5. Tool Configuration

Represents tool paths and runtime settings.

**Format**: Environment variables or command-line flags

**Attributes**:
- `KIND_CLUSTER_NAME`: Name of the KinD cluster (default: "structures-cluster")
- `KIND_CONFIG_PATH`: Path to kind-config.yaml (default: "config/kind-config.yaml")
- `HELM_VALUES_PATH`: Path to helm-values.yaml (default: "config/helm-values.yaml")
- `HELM_CHART_PATH`: Path to structures Helm chart (default: "./helm/structures")
- `IMAGE_NAME`: Full image name from bootBuildImage (default: read from gradle.properties)
- `IMAGE_VERSION`: Image version (default: from gradle.properties structuresVersion field)
- `SKIP_CHECKS`: Skip prerequisite checks (default: false)
- `VERBOSE`: Enable verbose logging (default: false)
- `DRY_RUN`: Print commands without executing (default: false)
- `DEPLOY_DEPS`: Deploy Elasticsearch and Keycloak (default: true)
- `DEPLOY_OBSERVABILITY`: Deploy observability stack (default: false)

**Validation Rules**:
- Paths must exist and be readable
- Boolean values must be true/false or 1/0
- NAME values must be valid Kubernetes resource names

### 6. Cluster State

Represents runtime state of KinD cluster (not persisted, queried from kubectl/kind CLI).

**Queried via**: `kind get clusters`, `kubectl cluster-info`, `kubectl get nodes`

**Attributes**:
- `exists`: Boolean - cluster exists
- `running`: Boolean - cluster is accessible
- `nodes`: Array of node information
  - `name`: Node name
  - `status`: Ready, NotReady
  - `role`: control-plane or worker
- `context`: Current kubectl context name
- `apiServer`: API server endpoint URL

### 7. Deployment State

Represents runtime state of Helm releases (not persisted, queried from helm CLI).

**Queried via**: `helm list`, `kubectl get pods`

**Attributes**:
- `releases`: Array of Helm releases
  - `name`: Release name (e.g., "structures-server", "keycloak", "elasticsearch")
  - `status`: deployed, failed, pending
  - `revision`: Release revision number
  - `chart`: Chart name and version
- `pods`: Array of pod status
  - `name`: Pod name
  - `ready`: Boolean - all containers ready
  - `status`: Running, Pending, Failed, CrashLoopBackOff
  - `restarts`: Number of container restarts

## Configuration Loading Priority

1. Command-line flags (highest priority)
2. Environment variables
3. Configuration files
4. Built-in defaults (lowest priority)

Example:
```bash
# Default
./kind-cluster.sh create

# Environment variable override
KIND_CLUSTER_NAME=test-cluster ./kind-cluster.sh create

# CLI flag override (highest priority)
./kind-cluster.sh create --name my-cluster --config custom-config.yaml
```

## State Transitions

### Cluster Lifecycle

```
[Non-existent] --create--> [Creating] --success--> [Running]
                                |
                                +--failure--> [Failed]
[Running] --delete--> [Deleting] --success--> [Non-existent]
```

### Deployment Lifecycle

```
[Not Deployed] --deploy--> [Deploying] --success--> [Deployed]
                                |
                                +--failure--> [Failed]
[Deployed] --deploy--> [Upgrading] --success--> [Deployed (new revision)]
                                |
                                +--failure--> [Failed] --rollback--> [Deployed (prev revision)]
```

## File System Layout

```
dev-tools/kind/
├── config/
│   ├── kind-config.yaml          # Cluster configuration
│   ├── helm-values.yaml          # Default Helm values
│   └── helm-values.local.yaml    # Local overrides (gitignored)
├── lib/
│   └── *.sh                      # Library functions
└── kind-cluster.sh               # Main script

~/.kube/
└── config                        # kubectl configuration (managed by kind CLI)
```

## Notes

- Configuration files use YAML for compatibility with Kubernetes ecosystem tools
- No application database or persistent storage - all state queried from Kubernetes APIs
- Local overrides (`helm-values.local.yaml`) allow per-developer customization without committing changes
- State is ephemeral - cluster and deployments can be recreated from configuration

