# Kinotic Spring Properties Reference

This document catalogs all Spring Boot configuration properties across the Kinotic application, maps them to the current Helm ConfigMap env vars, and proposes a `kubernetes` Spring profile to reduce ConfigMap complexity.

---

## Current Problem

The Helm ConfigMap (`kinotic-server-config-map.yaml`) sets **45+ environment variables** to configure the application. Many of these are static values that never change between deployments (ports, paths, feature flags). Environment variables should be reserved for values that are truly environment-specific (hosts, credentials, feature toggles).

Additionally, the env var names use legacy prefixes (`STRUCTURES_*`, `CONTINUUM_*`) that don't match the current `@ConfigurationProperties` prefixes (`kinotic.*`). The application has custom binding logic to bridge these, but it adds confusion.

---

## Property Sources by Module

### kinotic-core (`kinotic.*`)

```yaml
kinotic:
  # ── General ──────────────────────────────────────────────
  debug: false                          # Enable additional server info and error details
  disableClustering: false              # Disable clustering (also disables EventStreamService)
  maxNumberOfCoresToUse: <cpu-count>    # Max CPU cores (default: all available)
  maxOffHeapMemory: <ignite-default>    # Max off-heap memory for Ignite data regions
  sessionTimeout: 1800000              # Session timeout in ms (30 min)
  maxEventPayloadSize: 104857600       # Max event payload size (100MB)
  eventBusClusterHost: null            # Host for clustering event bus
  eventBusClusterPort: 0              # Port for clustering event bus (0 = random)
  eventBusClusterPublicHost: null      # Public-facing hostname
  eventBusClusterPublicPort: -1        # Public-facing port (-1 = same as cluster port)

  # ── Ignite Cluster Discovery ─────────────────────────────
  ignite:
    discoveryType: SHAREDFS            # LOCAL, SHAREDFS, or KUBERNETES
    discoveryPort: 47500               # Ignite discovery port
    communicationPort: 47100           # Ignite communication port
    joinTimeoutMs: 0                   # Cluster join timeout (0 = wait forever)
    localAddress: null                 # Bind address for inter-node communication
    localAddresses: null               # Comma-delimited addresses for LOCAL discovery
    sharedFsPath: "/tmp/structures-sharedfs"  # Path for SHAREDFS discovery
    workDirectory: "/tmp/ignite"       # Ignite work directory
    # Kubernetes discovery (when discoveryType: KUBERNETES)
    kubernetesNamespace: "default"
    kubernetesServiceName: "kinotic"
    kubernetesIncludeNotReadyAddresses: false
    kubernetesMasterUrl: null
    kubernetesAccountToken: null
```

**Source:** `KinoticProperties.java`, `IgniteProperties.java`

### kinotic-persistence (`kinotic.persistence.*`)

```yaml
kinotic:
  persistence:
    # ── Elasticsearch ────────────────────────────────────────
    elasticConnections:
      - host: "localhost"
        port: 9200
        scheme: "http"
    elasticUsername: null
    elasticPassword: null
    elasticConnectionTimeout: "5s"
    elasticSocketTimeout: "1m"
    elasticHealthCheckInterval: "1m"
    indexPrefix: "kinotic_"            # Elasticsearch index prefix (immutable)

    # ── HTTP Servers ─────────────────────────────────────────
    openApiSecurityType: NONE          # NONE, BASIC, or BEARER
    openApiPort: 8080                  # OpenAPI REST port
    openApiPath: "/api/"               # OpenAPI endpoint path
    openApiAdminPath: "/admin/api/"    # OpenAPI admin path
    openApiServerUrl: "http://localhost" # Advertised server URL
    graphqlPort: 4000                  # GraphQL port
    graphqlPath: "/graphql/"           # GraphQL endpoint path
    webServerPort: 9090                # Static files / health port
    healthCheckPath: "/health/"        # Health check path
    enableStaticFileServer: true       # Serve static files
    mcpPort: 3001                      # MCP server port

    # ── Multi-tenancy ────────────────────────────────────────
    tenantIdFieldName: "tenantId"
    structuresBaseUrl: "http://localhost"
    initializeWithSampleData: false    # Bootstrap sample data on first run

    # ── CORS ─────────────────────────────────────────────────
    corsAllowedOriginPattern: "http://localhost.*"
    corsAllowedHeaders:
      - Accept
      - Authorization
      - Content-Type
    corsAllowCredentials: null
    maxHttpHeaderSize: 8192
    maxHttpBodySize: -1                # -1 = unlimited

    # ── Cluster Eviction ─────────────────────────────────────
    clusterEviction:
      maxCacheSyncRetryAttempts: 3
      cacheSyncRetryDelayMs: 1000
      cacheSyncTimeoutMs: 30000
```

**Source:** `KinoticPersistenceProperties.java`, `PersistenceProperties.java`, `ClusterEvictionProperties.java`, `ElasticConnectionInfo.java`

### kinotic-rpc-gateway (`kinotic.rpcGateway.*`)

```yaml
kinotic:
  rpcGateway:
    enableCLIConnections: true
    stomp:
      port: 58503                      # STOMP WebSocket port
      websocketPath: "/v1"             # WebSocket endpoint path
```

**Source:** `KinoticRpcGatewayProperties.java`, `RpcGatewayProperties.java`

### OIDC Security (`oidc-security-service.*`)

```yaml
oidc-security-service:
  enabled: false
  debug: false
  tenantIdFieldName: "tenantId"
  frontendConfigurationPath: "/app-config.override.json"
  oidcProviders:
    - provider: "keycloak"
      displayName: "Keycloak"
      enabled: true
      clientId: "kinotic-client"
      audience: "kinotic-client"
      authority: "https://auth.example.com/realms/test"
      redirectUri: "https://app.example.com/login"
      postLogoutRedirectUri: "https://app.example.com"
      silentRedirectUri: "https://app.example.com/silent-renew.html"
      rolesClaimPath: "realm_access.roles"
      domains:
        - "example.com"
      roles:
        - "admin"
      additionalScopes: null
      metadata: {}
```

**Source:** `OidcSecurityServiceProperties.java`, `OidcProvider.java`

### kinotic-migration (`kinotic-migration.*`)

```yaml
kinotic-migration:
  elasticScheme: "http"                # required
  elasticHost: "localhost"             # required
  elasticPort: 9200                    # required
  elasticUsername: null                 # optional
  elasticPassword: null                # optional (use secretKeyRef in K8s)
```

**Source:** `MirationProperties.java`

### kinotic-telemetry (`kinotic.telemetry.*`)

```yaml
kinotic:
  telemetry:
    receiver:
      host: "0.0.0.0"
      port: 4317
      enabled: true
    buffer:
      path: "./data/telemetry-queue"
      rollCycle: "HOURLY"
      batchSize: 100
      flushIntervalMs: 1000
    output:
      type: "otlp-grpc"
      endpoint: "http://localhost:4317"
      maxRetries: 3
      retryDelayMs: 1000
      enabled: true
      timeoutMs: 5000
```

**Source:** `KinoticTelemetryProperties.java`

### Spring AI (`spring.ai.*`)

```yaml
spring:
  ai:
    openai:
      api-key: "noop"
      base-url: "https://api.x.ai"
      chat:
        options:
          model: "grok-4"
```

**Source:** Spring AI autoconfiguration (third-party)

---

## ConfigMap Env Vars (After Profile Adoption)

The Helm ConfigMap was reduced from ~45 env vars to ~12 by moving static config into Spring profiles. Only environment-specific values remain as env vars:

| Env Var (ConfigMap) | Spring Property | Notes |
|---------------------|-----------------|-------|
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | `production,kubernetes` or `production,kubernetes,kubernetes-oidc` |
| `JAVA_TOOL_OPTIONS` | JVM args | Buildpack/JVM tuning |
| `BPL_JVM_HEAD_ROOM` | Buildpack config | JVM heap headroom |
| `BPL_JAVA_NMT_ENABLED` | Buildpack config | Native Memory Tracking |
| `BPL_JMX_ENABLED` | Buildpack config | JMX monitoring |
| `KINOTIC_PERSISTENCE_ELASTICCONNECTIONS_0_SCHEME` | `kinotic.persistence.elasticConnections[0].scheme` | ES connection |
| `KINOTIC_PERSISTENCE_ELASTICCONNECTIONS_0_HOST` | `kinotic.persistence.elasticConnections[0].host` | ES connection |
| `KINOTIC_PERSISTENCE_ELASTICCONNECTIONS_0_PORT` | `kinotic.persistence.elasticConnections[0].port` | ES connection |
| `SPRING_AI_OPENAI_BASE_URL` | `spring.ai.openai.base-url` | AI provider |
| `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` | `spring.ai.openai.chat.options.model` | AI model |
| `SPRING_AI_OPENAI_API_KEY` | `spring.ai.openai.api-key` | AI API key |

**Credentials (via `secretKeyRef` in deployment, not ConfigMap):**

| Env Var | Spring Property | Secret Source |
|---------|-----------------|---------------|
| `KINOTIC_PERSISTENCE_ELASTICUSERNAME` | `kinotic.persistence.elasticUsername` | Helm value |
| `KINOTIC_PERSISTENCE_ELASTICPASSWORD` | `kinotic.persistence.elasticPassword` | `kinotic-es-es-elastic-user` (ECK) |

**OIDC providers (conditional, only when `kubernetes-oidc` profile is active):**

| Env Var | Notes |
|---------|-------|
| `OIDC_SECURITY_SERVICE_OIDC_PROVIDERS_*` | Provider details (authority, clientId, etc.) are environment-specific |

**What moved to Spring profiles:**
All `STRUCTURES_*` env vars (ports, paths, timeouts, index prefix, CORS, security type, etc.) and all `CONTINUUM_*` env vars (discovery type, cluster config, debug, resource limits) are now defined in `application-kubernetes.yml` and `application-kubernetes-oidc.yml`.

---

## Kubernetes Spring Profiles (Implemented)

Two profiles were created in `kinotic-server/src/main/resources/`:

### `application-kubernetes.yml` — Base Kubernetes Profile

Only contains properties that differ from code defaults and are required for Kubernetes:

```yaml
kinotic:
  ignite:
    discoveryType: KUBERNETES
    localAddress: "0.0.0.0"
    kubernetesIncludeNotReadyAddresses: true
  persistence:
    corsAllowedOriginPattern: "*"
```

Activated with: `SPRING_PROFILES_ACTIVE=production,kubernetes`

### `application-kubernetes-oidc.yml` — Keycloak/OIDC Overlay

Layers on top of the `kubernetes` profile to enable OIDC authentication:

```yaml
kinotic:
  persistence:
    openApiSecurityType: BEARER
oidc-security-service:
  enabled: true
```

Activated with: `SPRING_PROFILES_ACTIVE=production,kubernetes,kubernetes-oidc`

OIDC provider details (authority, clientId, redirectUri, etc.) remain as env vars since they vary per environment.

### Profile Composition

| Deployment | SPRING_PROFILES_ACTIVE |
|---|---|
| KinD (no auth) | `production,kubernetes,debug,eviction-tracking` |
| KinD (Keycloak) | `production,kubernetes,kubernetes-oidc,debug,eviction-tracking` |
| AKS (no auth) | `production,kubernetes` |
| AKS (Keycloak) | `production,kubernetes,kubernetes-oidc` |

---

## All Spring Profiles

| Profile | File | Purpose |
|---------|------|---------|
| `development` | `application-development.yml` | Local dev: SHAREDFS discovery, trace logging, local ES |
| `debug` | `application-debug.yml` | Verbose logging for all components |
| `clienttest` | `application-clienttest.yml` | Disables persistence for RPC-only testing |
| `production` | _(activated via env var)_ | Production settings (no dedicated file yet) |
| `eviction-tracking` | _(activated via env var)_ | Enables cache eviction CSV recording |
| `kubernetes` | `application-kubernetes.yml` | K8s discovery, bind 0.0.0.0, wildcard CORS |
| `kubernetes-oidc` | `application-kubernetes-oidc.yml` | Enables OIDC + BEARER auth (layers on kubernetes) |
