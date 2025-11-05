# Apache Ignite Cluster Configuration - Quick Reference

This document provides a quick reference for all Ignite cluster configuration options in Structures.

## Configuration Overview

Structures uses `StructuresProperties` for all Ignite cluster configuration. All properties are prefixed with `structures.cluster-*`.

## Discovery Type Selection

**Property**: `structures.cluster-discovery-type`  
**Java Constant**: `StructuresProperties.ClusterDiscoveryType.*`  
**Values**: `"local"`, `"sharedfs"`, `"kubernetes"`  
**Default**: `"local"`  
**Environment Variable**: `STRUCTURES_CLUSTER_DISCOVERY_TYPE`

### Discovery Type Guide

| Type | Use Case | Required Properties |
|------|----------|---------------------|
| `local` | Single-node, development | None |
| `sharedfs` | Docker Compose, VMs, multi-node | `cluster-shared-fs-addresses` |
| `kubernetes` | Kubernetes, OpenShift | `cluster-kubernetes-namespace`, `cluster-kubernetes-service-name` |

---

## All Configuration Properties

### Core Network Configuration

| Property | Type | Default | Environment Variable | Description |
|----------|------|---------|---------------------|-------------|
| `cluster-discovery-port` | Integer | `47500` | `STRUCTURES_CLUSTER_DISCOVERY_PORT` | Port for Ignite discovery protocol |
| `cluster-communication-port` | Integer | `47100` | `STRUCTURES_CLUSTER_COMMUNICATION_PORT` | Port for node communication |
| `cluster-join-timeout-ms` | Long | `30000` | `STRUCTURES_CLUSTER_JOIN_TIMEOUT_MS` | Cluster formation timeout (ms) |

### Shared FS (Static IP) Discovery

| Property | Type | Default | Environment Variable | Description |
|----------|------|---------|---------------------|-------------|
| `cluster-shared-fs-addresses` | String | `localhost:47500` | `STRUCTURES_CLUSTER_SHARED_FS_ADDRESSES` | Comma-separated node addresses |

**Format**: `host1:port1,host2:port2,host3:port3`  
**Example**: `node1:47500,node2:47500,node3:47500`

### Kubernetes Discovery

| Property | Type | Default | Environment Variable | Description |
|----------|------|---------|---------------------|-------------|
| `cluster-kubernetes-namespace` | String | `default` | `STRUCTURES_CLUSTER_KUBERNETES_NAMESPACE` | K8s namespace |
| `cluster-kubernetes-service-name` | String | `structures-ignite` | `STRUCTURES_CLUSTER_KUBERNETES_SERVICE_NAME` | Headless service name |
| `cluster-kubernetes-master-url` | String | `null` | `STRUCTURES_CLUSTER_KUBERNETES_MASTER_URL` | K8s API server URL (optional) |
| `cluster-kubernetes-account-token` | String | `null` | `STRUCTURES_CLUSTER_KUBERNETES_ACCOUNT_TOKEN` | Service account token (optional) |

**Notes**:
- `master-url` and `account-token` are optional - uses in-cluster config by default
- Requires `ignite-kubernetes` dependency (see below)
- Requires RBAC permissions for service account

### Cache Eviction Retry Configuration

| Property | Type | Default | Environment Variable | Description |
|----------|------|---------|---------------------|-------------|
| `max-cluster-sync-retry-attempts` | Integer | `3` | `STRUCTURES_MAX_CLUSTER_SYNC_RETRY_ATTEMPTS` | Max retry attempts |
| `cluster-sync-retry-delay-ms` | Long | `1000` | `STRUCTURES_CLUSTER_SYNC_RETRY_DELAY_MS` | Delay between retries (ms) |
| `cluster-sync-timeout-ms` | Long | `30000` | `STRUCTURES_CLUSTER_SYNC_TIMEOUT_MS` | Timeout per sync attempt (ms) |

---

## Configuration Examples

### Development (Local Single-Node)

**application.yml**:
```yaml
structures:
  cluster-discovery-type: local
```

**Environment Variables** (none required):
```bash
# Defaults to local mode
```

---

### Docker Compose (3-node cluster)

**application.yml**:
```yaml
structures:
  cluster-discovery-type: ${STRUCTURES_CLUSTER_DISCOVERY_TYPE:sharedfs}
  cluster-shared-fs-addresses: ${STRUCTURES_CLUSTER_SHARED_FS_ADDRESSES}
  cluster-discovery-port: ${STRUCTURES_CLUSTER_DISCOVERY_PORT:47500}
  cluster-communication-port: ${STRUCTURES_CLUSTER_COMMUNICATION_PORT:47100}
  cluster-join-timeout-ms: ${STRUCTURES_CLUSTER_JOIN_TIMEOUT_MS:30000}
```

**docker-compose.yml**:
```yaml
services:
  node1:
    environment:
      STRUCTURES_CLUSTER_DISCOVERY_TYPE: sharedfs
      STRUCTURES_CLUSTER_SHARED_FS_ADDRESSES: node1:47500,node2:47500,node3:47500
      STRUCTURES_CLUSTER_DISCOVERY_PORT: 47500
      STRUCTURES_CLUSTER_COMMUNICATION_PORT: 47100
      STRUCTURES_CLUSTER_JOIN_TIMEOUT_MS: 30000
```

---

### Kubernetes Production

**values.yaml**:
```yaml
properties:
  structures:
    clusterDiscoveryType: "kubernetes"
    clusterKubernetesNamespace: "production"
    clusterKubernetesServiceName: "structures-ignite"
    clusterDiscoveryPort: 47500
    clusterCommunicationPort: 47100
    clusterJoinTimeoutMs: 60000  # Higher for production
    maxClusterSyncRetryAttempts: 5
    clusterSyncTimeoutMs: 45000
```

**ConfigMap** (alternative):
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: structures-config
data:
  application.yml: |
    structures:
      cluster-discovery-type: kubernetes
      cluster-kubernetes-namespace: production
      cluster-kubernetes-service-name: structures-ignite
      cluster-discovery-port: 47500
      cluster-communication-port: 47100
      cluster-join-timeout-ms: 60000
```

---

## Java Constants Usage

You can use type-safe constants in code:

```java
import org.kinotic.structures.api.config.StructuresProperties.ClusterDiscoveryType;

// In configuration or logic
if (properties.getClusterDiscoveryType().equals(ClusterDiscoveryType.KUBERNETES)) {
    // Kubernetes-specific logic
}

// Or in conditionals
String discoveryType = properties.getClusterDiscoveryType();
switch (discoveryType) {
    case ClusterDiscoveryType.LOCAL:
        // Single-node
        break;
    case ClusterDiscoveryType.SHAREDFS:
        // Docker/VM cluster
        break;
    case ClusterDiscoveryType.KUBERNETES:
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

File: `structures-core/src/main/java/org/kinotic/structures/internal/config/IgniteConfiguration.java`

Uncomment the `TcpDiscoveryKubernetesIpFinder` implementation in the `createKubernetesIpFinder()` method.

### 3. Configure RBAC

Create service account with permissions (see `IGNITE_KUBERNETES_TUNING.md`).

---

## Validation

When the application starts, check logs for:

```
INFO  o.k.s.i.c.IgniteConfiguration - Initializing Apache Ignite with discovery type: kubernetes
INFO  o.k.s.i.c.IgniteConfiguration - Configuring KUBERNETES discovery - namespace: production, service: structures-ignite
INFO  o.k.s.i.c.IgniteConfiguration - Apache Ignite started successfully - Cluster size: 3
```

Cluster size should match your expected node count.

---

## Common Configuration Patterns

### Fast Failure Detection
```yaml
structures:
  cluster-discovery-type: kubernetes
  cluster-join-timeout-ms: 60000
  max-cluster-sync-retry-attempts: 5  # More retries
  cluster-sync-retry-delay-ms: 500    # Faster retries
  cluster-sync-timeout-ms: 15000      # Lower timeout per attempt
```

### Geo-Distributed Cluster
```yaml
structures:
  cluster-discovery-type: kubernetes
  cluster-join-timeout-ms: 120000     # Longer join time
  max-cluster-sync-retry-attempts: 3
  cluster-sync-retry-delay-ms: 2000   # Longer delays
  cluster-sync-timeout-ms: 60000      # Higher timeout
```

### High Availability Focus
```yaml
structures:
  cluster-discovery-type: kubernetes
  cluster-join-timeout-ms: 60000
  max-cluster-sync-retry-attempts: 10  # Many retries
  cluster-sync-retry-delay-ms: 1000
  cluster-sync-timeout-ms: 30000
```

---

## Environment Variable Mapping

All properties can be set via environment variables using Spring Boot's relaxed binding:

| Property | Environment Variable |
|----------|---------------------|
| `cluster-discovery-type` | `STRUCTURES_CLUSTER_DISCOVERY_TYPE` |
| `cluster-shared-fs-addresses` | `STRUCTURES_CLUSTER_SHARED_FS_ADDRESSES` |
| `cluster-kubernetes-namespace` | `STRUCTURES_CLUSTER_KUBERNETES_NAMESPACE` |
| `cluster-kubernetes-service-name` | `STRUCTURES_CLUSTER_KUBERNETES_SERVICE_NAME` |
| `cluster-kubernetes-master-url` | `STRUCTURES_CLUSTER_KUBERNETES_MASTER_URL` |
| `cluster-kubernetes-account-token` | `STRUCTURES_CLUSTER_KUBERNETES_ACCOUNT_TOKEN` |
| `cluster-discovery-port` | `STRUCTURES_CLUSTER_DISCOVERY_PORT` |
| `cluster-communication-port` | `STRUCTURES_CLUSTER_COMMUNICATION_PORT` |
| `cluster-join-timeout-ms` | `STRUCTURES_CLUSTER_JOIN_TIMEOUT_MS` |
| `max-cluster-sync-retry-attempts` | `STRUCTURES_MAX_CLUSTER_SYNC_RETRY_ATTEMPTS` |
| `cluster-sync-retry-delay-ms` | `STRUCTURES_CLUSTER_SYNC_RETRY_DELAY_MS` |
| `cluster-sync-timeout-ms` | `STRUCTURES_CLUSTER_SYNC_TIMEOUT_MS` |

---

## Troubleshooting Configuration

### How to check current configuration

**Via Logs** (on startup):
```
INFO  o.k.s.i.c.IgniteConfiguration - Initializing Apache Ignite with discovery type: sharedfs
INFO  o.k.s.i.c.IgniteConfiguration - Configured 3 discovery addresses
INFO  o.k.s.i.c.IgniteConfiguration - Apache Ignite started successfully - Cluster size: 3
```

**Via JMX** (if enabled):
- Connect to JMX port
- Navigate to `org.apache.ignite:group=SPIs,name=TcpDiscoverySpi`
- View discovery configuration

### Common Misconfigurations

| Issue | Symptom | Solution |
|-------|---------|----------|
| Wrong discovery type | Single node only | Verify `STRUCTURES_CLUSTER_DISCOVERY_TYPE` |
| Missing addresses | Cluster won't form | Set `STRUCTURES_CLUSTER_SHARED_FS_ADDRESSES` for sharedfs |
| Port conflicts | Bind errors | Change `STRUCTURES_CLUSTER_DISCOVERY_PORT` |
| Wrong namespace | Pods not discovered (K8s) | Verify `STRUCTURES_CLUSTER_KUBERNETES_NAMESPACE` matches deployment |
| Missing RBAC | Permission denied (K8s) | Configure service account permissions |

---

## See Also

- **Comprehensive Kubernetes Tuning**: `IGNITE_KUBERNETES_TUNING.md` - All advanced options
- **Cluster Testing Guide**: `docker-compose/CLUSTER_TESTING.md` - Testing procedures
- **Design Document**: `CACHE_EVICTION_DESIGN.md` - Architecture and design
- **Test Documentation**: `src/test/java/org/kinotic/structures/cluster/README.md`

---

**Last Updated**: February 13, 2025  
**Configuration System**: StructuresProperties-based (Spring Boot ConfigurationProperties)


