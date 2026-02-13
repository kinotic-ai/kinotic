# Apache Ignite Cluster Configuration - Quick Reference

This document provides a quick reference for all Ignite cluster configuration options in Structures.

## Configuration Overview

Structures uses `ContinuumProperties` for Ignite cluster configuration. All cluster properties are prefixed with `kinotic.cluster.*`.

## Discovery Type Selection

**Property**: `kinotic.cluster.discoveryType`  
**Java Enum**: `IgniteClusterDiscoveryType`  
**Values**: `LOCAL`, `SHAREDFS`, `KUBERNETES`  
**Default**: `LOCAL`  
**Environment Variable**: `CONTINUUM_CLUSTER_DISCOVERYTYPE`

### Discovery Type Guide

| Type | Use Case | Required Properties |
|------|----------|---------------------|
| `LOCAL` | Single-node, development | None |
| `SHAREDFS` | Docker Compose, VMs, multi-node | `localAddresses` |
| `KUBERNETES` | Kubernetes, OpenShift | `kubernetesNamespace`, `kubernetesServiceName` |

---

## All Configuration Properties

### Core Configuration

| Property | Type | Default | Environment Variable | Description |
|----------|------|---------|---------------------|-------------|
| `kinotic.disableClustering` | Boolean | `false` | `CONTINUUM_DISABLE_CLUSTERING` | Disable clustering entirely |

### Network Configuration

| Property | Type | Default | Environment Variable | Description |
|----------|------|---------|---------------------|-------------|
| `kinotic.cluster.discoveryPort` | Integer | `47500` | `CONTINUUM_CLUSTER_DISCOVERY_PORT` | Port for Ignite discovery protocol |
| `kinotic.cluster.communicationPort` | Integer | `47100` | `CONTINUUM_CLUSTER_COMMUNICATION_PORT` | Port for node communication |
| `kinotic.cluster.joinTimeoutMs` | Long | `0` | `CONTINUUM_CLUSTER_JOIN_TIMEOUT_MS` | Cluster formation timeout (0 = no timeout) |
| `kinotic.cluster.localAddress` | String | `null` | `CONTINUUM_CLUSTER_LOCAL_ADDRESS` | Local bind address |

### Shared FS (Static IP) Discovery

| Property | Type | Default | Environment Variable | Description |
|----------|------|---------|---------------------|-------------|
| `kinotic.cluster.localAddresses` | String | `null` | `CONTINUUM_CLUSTER_LOCAL_ADDRESSES` | Comma-separated node addresses |
| `kinotic.cluster.sharedFsPath` | String | `/tmp/structures-sharedfs` | `CONTINUUM_CLUSTER_SHARED_FS_PATH` | Path for shared filesystem |

**Format**: `host1:port1,host2:port2,host3:port3`  
**Example**: `node1:47500,node2:47500,node3:47500`

### Kubernetes Discovery

| Property | Type | Default | Environment Variable | Description |
|----------|------|---------|---------------------|-------------|
| `kinotic.cluster.kubernetesNamespace` | String | `default` | `CONTINUUM_CLUSTER_KUBERNETES_NAMESPACE` | K8s namespace |
| `kinotic.cluster.kubernetesServiceName` | String | `structures` | `CONTINUUM_CLUSTER_KUBERNETES_SERVICE_NAME` | Headless service name |
| `kinotic.cluster.kubernetesMasterUrl` | String | `null` | `CONTINUUM_CLUSTER_KUBERNETES_MASTER_URL` | K8s API server URL (optional) |
| `kinotic.cluster.kubernetesAccountToken` | String | `null` | `CONTINUUM_CLUSTER_KUBERNETES_ACCOUNT_TOKEN` | Service account token (optional) |
| `kinotic.cluster.kubernetesIncludeNotReadyAddresses` | Boolean | `false` | `CONTINUUM_CLUSTER_KUBERNETES_INCLUDE_NOT_READY_ADDRESSES` | Include pods not ready |

**Notes**:
- `kubernetesMasterUrl` and `kubernetesAccountToken` are optional - uses in-cluster config by default
- Requires `ignite-kubernetes` dependency (see below)
- Requires RBAC permissions for service account

### Cache Eviction Retry Configuration (via StructuresProperties)

| Property | Type | Default | Environment Variable | Description |
|----------|------|---------|---------------------|-------------|
| `structures.clusterEviction.maxCacheSyncRetryAttempts` | Integer | `3` | `STRUCTURES_CLUSTEREVICTION_MAXCACHESYNCRETRYATTEMPTS` | Max retry attempts |
| `structures.clusterEviction.cacheSyncRetryDelayMs` | Long | `1000` | `STRUCTURES_CLUSTEREVICTION_CACHESYNCRETRYDELAYMS` | Delay between retries (ms) |
| `structures.clusterEviction.cacheSyncTimeoutMs` | Long | `30000` | `STRUCTURES_CLUSTEREVICTION_CACHESYNCTIMEOUTMS` | Timeout per sync attempt (ms) |

---

## Configuration Examples

### Development (Local Single-Node)

**application.yml**:
```yaml
kinotic:
  disableClustering: false
  cluster:
    discoveryType: LOCAL
```

**Environment Variables** (none required):
```bash
# Defaults to LOCAL mode
```

---

### Docker Compose (3-node cluster)

**application.yml**:
```yaml
kinotic:
  disableClustering: false
  cluster:
    discoveryType: SHAREDFS
    localAddresses: "node1:47500,node2:47500,node3:47500"
    discoveryPort: 47500
    communicationPort: 47100
    joinTimeoutMs: 30000
```

**docker-compose.yml**:
```yaml
services:
  node1:
    environment:
      CONTINUUM_CLUSTER_DISCOVERY_TYPE: SHAREDFS
      CONTINUUM_CLUSTER_LOCAL_ADDRESSES: node1:47500,node2:47500,node3:47500
      CONTINUUM_CLUSTER_DISCOVERY_PORT: 47500
      CONTINUUM_CLUSTER_COMMUNICATION_PORT: 47100
      CONTINUUM_CLUSTER_JOIN_TIMEOUT_MS: 30000
```

---

### Kubernetes Production

**application.yml** (or ConfigMap):
```yaml
kinotic:
  disableClustering: false
  cluster:
    discoveryType: KUBERNETES
    kubernetesNamespace: production
    kubernetesServiceName: structures-ignite
    discoveryPort: 47500
    communicationPort: 47100
    joinTimeoutMs: 60000
```

**Environment Variables**:
```bash
CONTINUUM_CLUSTER_DISCOVERY_TYPE=KUBERNETES
CONTINUUM_CLUSTER_KUBERNETES_NAMESPACE=production
CONTINUUM_CLUSTER_KUBERNETES_SERVICE_NAME=structures-ignite
CONTINUUM_CLUSTER_DISCOVERY_PORT=47500
CONTINUUM_CLUSTER_COMMUNICATION_PORT=47100
CONTINUUM_CLUSTER_JOIN_TIMEOUT_MS=60000
```

---

## Java Constants Usage

You can use type-safe constants in code:

```java
import org.kinotic.core.api.config.IgniteClusterDiscoveryType;

// In configuration or logic
if(properties.getDiscoveryType() ==IgniteClusterDiscoveryType.KUBERNETES){
        // Kubernetes-specific logic
        }

// Or in conditionals
IgniteClusterDiscoveryType discoveryType = properties.getDiscoveryType();
switch(discoveryType){
        case LOCAL:
        // Single-node
        break;
        case SHAREDFS:
        // Docker/VM cluster
        break;
        case KUBERNETES:
        // K8s cluster
        break;
        }
```

---

## Enabling Kubernetes Discovery

Kubernetes discovery requires an additional dependency. To enable:

### 1. Update build.gradle

```gradle
dependencies {
    implementation 'org.apache.ignite:ignite-core'
    implementation 'org.apache.ignite:ignite-kubernetes'  // Add this
}
```

### 2. Uncomment code in IgniteConfiguration

File: `kinotic-core-vertx/src/main/java/org/kinotic/kinotic/internal/config/ContinuumIgniteConfig.java`

Enable the `TcpDiscoveryKubernetesIpFinder` implementation.

### 3. Configure RBAC

Create service account with permissions (see `IGNITE_KUBERNETES_TUNING.md`).

---

## Validation

When the application starts, check logs for:

```
INFO - Initializing Apache Ignite with discovery type: KUBERNETES
INFO - Configuring KUBERNETES discovery - namespace: production, service: structures-ignite
INFO - Apache Ignite started successfully - Cluster size: 3
```

Cluster size should match your expected node count.

---

## Common Configuration Patterns

### Fast Failure Detection
```yaml
kinotic:
  cluster:
    discoveryType: KUBERNETES
    joinTimeoutMs: 60000

structures:
  clusterEviction:
    maxCacheSyncRetryAttempts: 5
    cacheSyncRetryDelayMs: 500
    cacheSyncTimeoutMs: 15000
```

### Geo-Distributed Cluster
```yaml
kinotic:
  cluster:
    discoveryType: KUBERNETES
    joinTimeoutMs: 120000

structures:
  clusterEviction:
    maxCacheSyncRetryAttempts: 3
    cacheSyncRetryDelayMs: 2000
    cacheSyncTimeoutMs: 60000
```

### High Availability Focus
```yaml
kinotic:
  cluster:
    discoveryType: KUBERNETES
    joinTimeoutMs: 60000

structures:
  clusterEviction:
    maxCacheSyncRetryAttempts: 10
    cacheSyncRetryDelayMs: 1000
    cacheSyncTimeoutMs: 30000
```

---

## Environment Variable Mapping

All properties can be set via environment variables. The Helm ConfigMap uses underscore-separated names:

| Property | Environment Variable |
|----------|---------------------|
| `kinotic.disableClustering` | `CONTINUUM_DISABLE_CLUSTERING` |
| `kinotic.cluster.discoveryType` | `CONTINUUM_CLUSTER_DISCOVERY_TYPE` |
| `kinotic.cluster.localAddresses` | `CONTINUUM_CLUSTER_LOCAL_ADDRESSES` |
| `kinotic.cluster.localAddress` | `CONTINUUM_CLUSTER_LOCAL_ADDRESS` |
| `kinotic.cluster.discoveryPort` | `CONTINUUM_CLUSTER_DISCOVERY_PORT` |
| `kinotic.cluster.communicationPort` | `CONTINUUM_CLUSTER_COMMUNICATION_PORT` |
| `kinotic.cluster.joinTimeoutMs` | `CONTINUUM_CLUSTER_JOIN_TIMEOUT_MS` |
| `kinotic.cluster.sharedFsPath` | `CONTINUUM_CLUSTER_SHARED_FS_PATH` |
| `kinotic.cluster.kubernetesNamespace` | `CONTINUUM_CLUSTER_KUBERNETES_NAMESPACE` |
| `kinotic.cluster.kubernetesServiceName` | `CONTINUUM_CLUSTER_KUBERNETES_SERVICE_NAME` |
| `kinotic.cluster.kubernetesMasterUrl` | `CONTINUUM_CLUSTER_KUBERNETES_MASTER_URL` |
| `kinotic.cluster.kubernetesAccountToken` | `CONTINUUM_CLUSTER_KUBERNETES_ACCOUNT_TOKEN` |

---

## Troubleshooting Configuration

### How to check current configuration

**Via Logs** (on startup):
```
INFO - Initializing Apache Ignite with discovery type: SHAREDFS
INFO - Configured 3 discovery addresses
INFO - Apache Ignite started successfully - Cluster size: 3
```

**Via JMX** (if enabled):
- Connect to JMX port
- Navigate to `org.apache.ignite:group=SPIs,name=TcpDiscoverySpi`
- View discovery configuration

### Common Misconfigurations

| Issue | Symptom | Solution |
|-------|---------|----------|
| Wrong discovery type | Single node only | Verify `CONTINUUM_CLUSTER_DISCOVERY_TYPE` |
| Missing addresses | Cluster won't form | Set `CONTINUUM_CLUSTER_LOCAL_ADDRESSES` for SHAREDFS |
| Port conflicts | Bind errors | Change `CONTINUUM_CLUSTER_DISCOVERY_PORT` |
| Wrong namespace | Pods not discovered (K8s) | Verify `CONTINUUM_CLUSTER_KUBERNETES_NAMESPACE` matches deployment |
| Missing RBAC | Permission denied (K8s) | Configure service account permissions |

---

## See Also

- **Comprehensive Kubernetes Tuning**: `IGNITE_KUBERNETES_TUNING.md` - All advanced options
- **Cluster Testing Guide**: `docker-compose/CLUSTER_TESTING.md` - Testing procedures
- **Design Document**: `CACHE_EVICTION_DESIGN.md` - Architecture and design
- **Test Documentation**: `src/test/java/org/kinotic/structures/cluster/README.md`

---

**Last Updated**: January 2026  
**Configuration Prefix**: `kinotic.cluster.*` (ContinuumProperties)
