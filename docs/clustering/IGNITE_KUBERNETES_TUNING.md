# Apache Ignite Cluster Configuration - Complete Tuning Guide

This document provides a comprehensive guide to all tunable options for Apache Ignite cluster configuration in Structures, with a focus on Kubernetes deployment.

---

## Currently Implemented Options (via ContinuumProperties)

These options are currently exposed and configurable through `continuum.cluster.*` properties:

### Core Configuration
| Property | Default | Type | Description |
|----------|---------|------|-------------|
| `continuum.cluster.discoveryType` | `LOCAL` | Enum | Discovery mechanism: `LOCAL`, `SHAREDFS`, `KUBERNETES` |
| `continuum.cluster.discoveryPort` | `47500` | Integer | Port for discovery protocol |
| `continuum.cluster.communicationPort` | `47100` | Integer | Port for node-to-node communication |
| `continuum.cluster.joinTimeoutMs` | `0` | Long | Timeout for joining cluster (0 = no timeout) |
| `continuum.cluster.localAddress` | `null` | String | Local bind address |

### Shared FS (Static IP) Discovery
| Property | Default | Type | Description |
|----------|---------|------|-------------|
| `continuum.cluster.localAddresses` | `null` | String | Comma-separated list: `"host1:port1,host2:port2"` |
| `continuum.cluster.sharedFsPath` | `/tmp/structures-sharedfs` | String | Path for shared filesystem |

### Kubernetes Discovery
| Property | Default | Type | Description |
|----------|---------|------|-------------|
| `continuum.cluster.kubernetesNamespace` | `default` | String | K8s namespace for pod discovery |
| `continuum.cluster.kubernetesServiceName` | `structures` | String | Headless service name |
| `continuum.cluster.kubernetesMasterUrl` | `null` | String | K8s API server URL (optional, uses in-cluster if null) |
| `continuum.cluster.kubernetesAccountToken` | `null` | String | Service account token (optional, uses mounted if null) |
| `continuum.cluster.kubernetesIncludeNotReadyAddresses` | `false` | Boolean | Include pods not ready |

### Cache Eviction Retry (via StructuresProperties)
| Property | Default | Type | Description |
|----------|---------|------|-------------|
| `structures.clusterEviction.maxCacheSyncRetryAttempts` | `3` | Integer | Max retry attempts for cluster eviction |
| `structures.clusterEviction.cacheSyncRetryDelayMs` | `1000` | Long | Delay between retries (ms) |
| `structures.clusterEviction.cacheSyncTimeoutMs` | `30000` | Long | Timeout per sync attempt (ms) |

---

## Current Implementation Status

| Feature | Status | Notes |
|---------|--------|-------|
| Local Discovery | ✅ Implemented | Ready to use |
| Shared FS Discovery | ✅ Implemented | Ready for Docker/VMs |
| Kubernetes Discovery | ⚠️ Prepared | Requires `ignite-kubernetes` dependency |
| Basic Network Config | ✅ Implemented | Ports, timeouts configured |
| K8s Service Integration | ✅ Documented | Helm templates ready |
| Advanced Tuning | 📋 Documented | Can be added as needed |

---

## Prerequisites

To use Kubernetes discovery, you need to add the `ignite-kubernetes` dependency:

**build.gradle**:
```gradle
dependencies {
    implementation 'org.apache.ignite:ignite-core'
    implementation 'org.apache.ignite:ignite-kubernetes'  // Required for K8s discovery
}
```

Then uncomment the Kubernetes IP Finder implementation in:
- `structures-core/src/main/java/org/mindignited/structures/internal/config/IgniteConfiguration.java`

## Overview

The Kubernetes IP Finder (`TcpDiscoveryKubernetesIpFinder`) uses the Kubernetes API to discover other Ignite nodes in the cluster. This eliminates the need for static IP addresses and works seamlessly with Kubernetes pod scheduling.

**Configuration via ContinuumProperties**:
```yaml
continuum:
  disableClustering: false
  cluster:
    discoveryType: KUBERNETES
    kubernetesNamespace: production
    kubernetesServiceName: structures-ignite
```

## Required Configuration

### 1. Namespace (`kubernetesNamespace`)
**Property**: `continuum.cluster.kubernetesNamespace`  
**Java**: `ipFinder.setNamespace(String namespace)`  
**Required**: YES  
**Default**: `"default"`

The Kubernetes namespace where your Structures pods are deployed.

**Example**:
```yaml
continuum:
  cluster:
    kubernetesNamespace: production
```

**Usage**: Must match the namespace in your Helm deployment.

---

### 2. Service Name (`kubernetesServiceName`)
**Property**: `continuum.cluster.kubernetesServiceName`  
**Java**: `ipFinder.setServiceName(String serviceName)`  
**Required**: YES  
**Default**: `"structures"`

The name of the **headless service** used for pod discovery.

**Example**:
```yaml
continuum:
  cluster:
    kubernetesServiceName: structures-ignite
```

**Headless Service** (must be created):
```yaml
apiVersion: v1
kind: Service
metadata:
  name: structures-ignite
spec:
  clusterIP: None  # Headless
  selector:
    app: structures
  ports:
    - name: discovery
      port: 47500
```

**Usage**: Kubernetes DNS returns all pod IPs for this service.

---

## Optional Configuration

### 3. Master URL (`kubernetesMasterUrl`)
**Property**: `continuum.cluster.kubernetesMasterUrl`  
**Java**: `ipFinder.setMasterUrl(String masterUrl)`  
**Required**: NO  
**Default**: Uses in-cluster configuration  

The Kubernetes API server URL.

**When to use**:
- Running outside the cluster (for testing)
- Custom API server endpoint
- Multi-cluster setups

**Example**:
```yaml
continuum:
  cluster:
    # In-cluster (default - leave null)
    kubernetesMasterUrl: null

    # External access (when running outside cluster)
    # kubernetesMasterUrl: "https://k8s-api.example.com:6443"
```

**Best Practice**: Leave null when running inside Kubernetes. The IP Finder will automatically use `kubernetes.default.svc` from the pod's environment.

---

### 4. Account Token (`kubernetesAccountToken`)
**Property**: `continuum.cluster.kubernetesAccountToken`  
**Java**: `ipFinder.setAccountToken(String token)`  
**Required**: NO  
**Default**: Uses mounted service account token  

The service account token for Kubernetes API authentication.

**When to use**:
- Custom service accounts
- External cluster access
- Testing/development

**Example**:
```yaml
continuum:
  cluster:
    # In-cluster (default - leave null)
    kubernetesAccountToken: null

    # Custom token (when running outside cluster)
    # kubernetesAccountToken: "eyJhbGciOiJSUzI1NiIsImtpZCI6..."
```

**Best Practice**: Leave null. Kubernetes automatically mounts the service account token at `/var/run/secrets/kubernetes.io/serviceaccount/token`.

---

## Advanced Tuning Options

The following options are available in the Ignite API but not currently exposed as `ContinuumProperties`. They can be added if needed:

### 5. Label Selector (`setLabelSelector()`)
**Java**: `ipFinder.setLabelSelector(String selector)`  
**Purpose**: Filter pods by Kubernetes labels  
**Default**: No filtering (uses service selector)

**Example**:
```java
ipFinder.setLabelSelector("app=structures,tier=backend");
```

**Use Case**: When you have multiple Structures deployments in the same namespace and want to create separate clusters.

**To Add**:
```java
// In StructuresProperties
private String clusterKubernetesLabelSelector = null;

// In IgniteConfiguration
if (properties.getClusterKubernetesLabelSelector() != null) {
    ipFinder.setLabelSelector(properties.getClusterKubernetesLabelSelector());
}
```

---

### 6. Client Mode (`setClientMode()`)
**Java**: `cfg.setClientMode(boolean clientMode)`  
**Purpose**: Run Ignite as client node (doesn't store data or participate in topology)  
**Default**: `false` (server mode)

**Example**:
```java
cfg.setClientMode(true);  // Client node
```

**Use Case**: Frontend/API nodes that query the cluster but don't store data.

**To Add**:
```java
// In StructuresProperties
private Boolean clusterClientMode = false;

// In IgniteConfiguration
cfg.setClientMode(properties.getClusterClientMode());
```

---

### 7. Connection Timeout (`setConnectionTimeout()`)
**Java**: `ipFinder.setConnectionTimeout(int timeout)`  
**Purpose**: Timeout for K8s API connections (milliseconds)  
**Default**: 0 (no timeout)

**Example**:
```java
ipFinder.setConnectionTimeout(5000);  // 5 seconds
```

**Use Case**: Slow or unstable Kubernetes API servers.

**To Add**:
```java
// In StructuresProperties
private Integer clusterKubernetesConnectionTimeoutMs = 5000;

// In IgniteConfiguration
ipFinder.setConnectionTimeout(properties.getClusterKubernetesConnectionTimeoutMs());
```

---

### 8. Read Timeout (`setReadTimeout()`)
**Java**: `ipFinder.setReadTimeout(int timeout)`  
**Purpose**: Timeout for reading K8s API responses (milliseconds)  
**Default**: 0 (no timeout)

**Example**:
```java
ipFinder.setReadTimeout(10000);  // 10 seconds
```

**Use Case**: Large clusters with slow API responses.

**To Add**:
```java
// In StructuresProperties
private Integer clusterKubernetesReadTimeoutMs = 10000;

// In IgniteConfiguration
ipFinder.setReadTimeout(properties.getClusterKubernetesReadTimeoutMs());
```

---

### 9. Shared (`setShared()`)
**Java**: `ipFinder.setShared(boolean shared)`  
**Purpose**: Whether IP finder is shared between multiple Ignite instances in same JVM  
**Default**: `false`

**Example**:
```java
ipFinder.setShared(true);
```

**Use Case**: Rare - only when running multiple Ignite instances in one JVM.

**Recommendation**: Leave as default (`false`) unless you have multiple Ignite instances per pod.

---

## Discovery SPI Tuning Options

These apply to the `TcpDiscoverySpi` itself (not the IP finder):

### 10. Join Timeout (`setJoinTimeout()`)
**Property**: `continuum.cluster.joinTimeoutMs`  
**Java**: `discoverySpi.setJoinTimeout(long timeout)`  
**Default**: `0` (no timeout)  
**Current**: ✅ Already configured

**Purpose**: Maximum time to wait for joining the cluster.

**Tuning Guide**:
- **Development**: 10-15 seconds
- **Production**: 30-60 seconds
- **Large clusters**: 60-120 seconds

---

### 11. Network Timeout (`setNetworkTimeout()`)
**Java**: `discoverySpi.setNetworkTimeout(long timeout)`  
**Default**: `5000` (5 seconds)

**Purpose**: Timeout for network operations during discovery.

**Use Case**: Slow networks or large clusters.

**To Add**:
```java
// In StructuresProperties
private Long clusterNetworkTimeoutMs = 5000L;

// In IgniteConfiguration
discoverySpi.setNetworkTimeout(properties.getClusterNetworkTimeoutMs());
```

---

### 12. Socket Timeout (`setSocketTimeout()`)
**Java**: `discoverySpi.setSocketTimeout(long timeout)`  
**Default**: `5000` (5 seconds)

**Purpose**: Timeout for socket operations.

**To Add**:
```java
// In StructuresProperties
private Long clusterSocketTimeoutMs = 5000L;

// In IgniteConfiguration
discoverySpi.setSocketTimeout(properties.getClusterSocketTimeoutMs());
```

---

### 13. Acknowledgement Timeout (`setAckTimeout()`)
**Java**: `discoverySpi.setAckTimeout(long timeout)`  
**Default**: `5000` (5 seconds)

**Purpose**: Timeout for receiving acknowledgements from other nodes.

**Tuning Guide**:
- Increase if nodes are geographically distributed
- Increase for clusters with high network latency

**To Add**:
```java
// In StructuresProperties
private Long clusterAckTimeoutMs = 5000L;

// In IgniteConfiguration
discoverySpi.setAckTimeout(properties.getClusterAckTimeoutMs());
```

---

### 14. Maximum Acknowledgement Timeout (`setMaxAckTimeout()`)
**Java**: `discoverySpi.setMaxAckTimeout(long timeout)`  
**Default**: `600000` (10 minutes)

**Purpose**: Maximum acknowledgement timeout (used during heavy load).

---

### 15. Reconnect Count (`setReconnectCount()`)
**Java**: `discoverySpi.setReconnectCount(int count)`  
**Default**: `10`

**Purpose**: Number of reconnection attempts if a node becomes unreachable.

**To Add**:
```java
// In StructuresProperties
private Integer clusterReconnectAttempts = 10;

// In IgniteConfiguration
discoverySpi.setReconnectCount(properties.getClusterReconnectAttempts());
```

---

### 16. Heartbeat Frequency (`setHeartbeatFrequency()`)
**Java**: `discoverySpi.setHeartbeatFrequency(long freq)`  
**Default**: `2000` (2 seconds)

**Purpose**: How often nodes send heartbeats to each other.

**Tuning Guide**:
- **Lower** (1000ms) = Faster failure detection, more network traffic
- **Higher** (5000ms) = Slower failure detection, less network traffic

**To Add**:
```java
// In StructuresProperties
private Long clusterHeartbeatFrequencyMs = 2000L;

// In IgniteConfiguration
discoverySpi.setHeartbeatFrequency(properties.getClusterHeartbeatFrequencyMs());
```

---

### 17. Statistics Print Frequency (`setStatisticsPrintFrequency()`)
**Java**: `discoverySpi.setStatisticsPrintFrequency(long freq)`  
**Default**: `0` (disabled)

**Purpose**: How often to print discovery statistics to logs.

**Use Case**: Debugging cluster formation issues.

---

## Communication SPI Tuning Options

These apply to `TcpCommunicationSpi`:

### 18. Connect Timeout (`setConnectTimeout()`)
**Java**: `commSpi.setConnectTimeout(long timeout)`  
**Default**: `5000` (5 seconds)

**Purpose**: Timeout for establishing connections between nodes.

**To Add**:
```java
// In StructuresProperties
private Long clusterCommConnectTimeoutMs = 5000L;

// In IgniteConfiguration
commSpi.setConnectTimeout(properties.getClusterCommConnectTimeoutMs());
```

---

### 19. Idle Connection Timeout (`setIdleConnectionTimeout()`)
**Java**: `commSpi.setIdleConnectionTimeout(long timeout)`  
**Default**: `600000` (10 minutes)

**Purpose**: Close idle connections after this timeout.

---

### 20. Socket Write Timeout (`setSocketWriteTimeout()`)
**Java**: `commSpi.setSocketWriteTimeout(long timeout)`  
**Default**: `2000` (2 seconds)

**Purpose**: Timeout for writing to sockets.

---

### 21. Connections Per Node (`setConnectionsPerNode()`)
**Java**: `commSpi.setConnectionsPerNode(int count)`  
**Default**: `1`

**Purpose**: Number of parallel connections between nodes.

**Tuning Guide**:
- **1** = Default, sufficient for most cases
- **2-4** = High throughput scenarios
- **>4** = Only for very high-bandwidth requirements

---

### 22. Shared Memory Port (`setSharedMemoryPort()`)
**Java**: `commSpi.setSharedMemoryPort(int port)`  
**Default**: `48100`

**Purpose**: Port for shared memory communication (same-host optimization).

**Note**: Not relevant for Kubernetes (pods don't share memory).

---

## Recommended Production Configuration

### Minimal (Good Starting Point)
```yaml
continuum:
  disableClustering: false
  cluster:
    discoveryType: KUBERNETES
    kubernetesNamespace: production
    kubernetesServiceName: structures-ignite
    discoveryPort: 47500
    communicationPort: 47100
    joinTimeoutMs: 60000  # 60 seconds for large clusters
```

### Optimized for Fast Failure Detection
```yaml
continuum:
  disableClustering: false
  cluster:
    discoveryType: KUBERNETES
    kubernetesNamespace: production
    kubernetesServiceName: structures-ignite
    discoveryPort: 47500
    communicationPort: 47100
    joinTimeoutMs: 60000
    # Advanced options (if exposed in future):
    # heartbeatFrequencyMs: 1000  # Fast failure detection (1s)
    # ackTimeoutMs: 3000  # Lower timeout
    # reconnectAttempts: 5  # Fewer retries
```

### Optimized for Geo-Distributed Clusters
```yaml
continuum:
  disableClustering: false
  cluster:
    discoveryType: KUBERNETES
    kubernetesNamespace: production
    kubernetesServiceName: structures-ignite
    discoveryPort: 47500
    communicationPort: 47100
    joinTimeoutMs: 120000  # 2 minutes
    # Advanced options (if exposed in future):
    # networkTimeoutMs: 15000  # Higher for latency
    # socketTimeoutMs: 15000
    # ackTimeoutMs: 15000
    # heartbeatFrequencyMs: 5000  # Less frequent
```

---

## Kubernetes RBAC Requirements

For the IP Finder to work, the pod's service account needs permissions:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: structures
  namespace: production
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: structures-ignite-discovery
  namespace: production
rules:
  # Required: List endpoints in the namespace
  - apiGroups: [""]
    resources: ["endpoints"]
    verbs: ["get", "list"]
  # Required: List pods in the namespace
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: structures-ignite-discovery
  namespace: production
subjects:
  - kind: ServiceAccount
    name: structures
    namespace: production
roleRef:
  kind: Role
  name: structures-ignite-discovery
  apiGroup: rbac.authorization.k8s.io
```

---

## Complete Tunable Options Summary

| Option | Property Name | Default | When to Tune |
|--------|---------------|---------|--------------|
| **Namespace** | `cluster-kubernetes-namespace` | `default` | Always set to your namespace |
| **Service Name** | `cluster-kubernetes-service-name` | `structures-ignite` | Match your headless service |
| **Master URL** | `cluster-kubernetes-master-url` | null (in-cluster) | External access only |
| **Account Token** | `cluster-kubernetes-account-token` | null (auto) | Custom auth only |
| **Discovery Port** | `cluster-discovery-port` | `47500` | Port conflicts |
| **Communication Port** | `cluster-communication-port` | `47100` | Port conflicts |
| **Join Timeout** | `cluster-join-timeout-ms` | `30000` | Large/slow clusters |
| Label Selector | (not exposed) | null | Multiple clusters in namespace |
| Connection Timeout | (not exposed) | `0` | Slow K8s API |
| Read Timeout | (not exposed) | `0` | Slow K8s API |
| Network Timeout | (not exposed) | `5000` | High latency networks |
| Socket Timeout | (not exposed) | `5000` | High latency networks |
| Ack Timeout | (not exposed) | `5000` | Geo-distributed clusters |
| Max Ack Timeout | (not exposed) | `600000` | Heavy load scenarios |
| Reconnect Count | (not exposed) | `10` | Unstable networks |
| Heartbeat Frequency | (not exposed) | `2000` | Failure detection tuning |
| Connections Per Node | (not exposed) | `1` | High throughput |

---

## How to Add Additional Tuning Options

If you need to expose more options, follow this pattern:

### 1. Add to StructuresProperties
```java
/**
 * Kubernetes API connection timeout in milliseconds
 */
private Integer clusterKubernetesConnectionTimeoutMs = 5000;
```

### 2. Use in IgniteConfiguration
```java
if (properties.getClusterKubernetesConnectionTimeoutMs() != null) {
    ipFinder.setConnectionTimeout(properties.getClusterKubernetesConnectionTimeoutMs());
}
```

### 3. Document in application.yml
```yaml
continuum:
  cluster:
    kubernetesConnectionTimeoutMs: 5000
```

### 4. Set via Environment Variable
```bash
CONTINUUM_CLUSTER_KUBERNETES_CONNECTION_TIMEOUT_MS=5000
```

---

## Troubleshooting

### Nodes Not Discovering Each Other

**Symptoms**: Nodes start but cluster size stays at 1

**Check**:
1. Service account has correct RBAC permissions
2. Headless service exists: `kubectl get svc structures-ignite`
3. Service selector matches pod labels
4. Namespace is correct
5. Check logs for "Failed to get Kubernetes endpoints" errors

**Solutions**:
- Verify RBAC: `kubectl auth can-i list endpoints --as=system:serviceaccount:production:structures -n production`
- Check service: `kubectl describe svc structures-ignite -n production`
- Verify pod labels match service selector

---

### Slow Cluster Formation

**Symptoms**: Takes > 1 minute for nodes to join cluster

**Solutions**:
- Increase `continuum.cluster.joinTimeoutMs` to prevent premature failures
- Check Kubernetes API performance
- Verify network policies allow pod-to-pod communication on discovery port
- Check for DNS resolution issues

---

### Frequent Reconnections

**Symptoms**: Logs show repeated "Node left topology" and "Node joined topology"

**Solutions**:
- Increase heartbeat frequency (if exposed)
- Increase acknowledgement timeout (if exposed)
- Increase network timeout (if exposed)
- Check pod resource limits (CPU throttling can cause timeouts)
- Verify network stability between pods

---

## Example Configurations by Environment

### Development (Local Single-Node)
```yaml
continuum:
  disableClustering: false
  cluster:
    discoveryType: LOCAL
    discoveryPort: 47500
    communicationPort: 47100
```

### Docker Compose Testing
```yaml
continuum:
  disableClustering: false
  cluster:
    discoveryType: SHAREDFS
    localAddresses: "node1:47500,node2:47500,node3:47500"
    discoveryPort: 47500
    communicationPort: 47100
    joinTimeoutMs: 30000
```

### Kubernetes Production
```yaml
continuum:
  disableClustering: false
  cluster:
    discoveryType: KUBERNETES
    kubernetesNamespace: production
    kubernetesServiceName: structures-ignite
    discoveryPort: 47500
    communicationPort: 47100
    joinTimeoutMs: 60000  # Higher for production
```

---

## Additional IgniteConfiguration Options

These options are available in Apache Ignite but not currently exposed via `ContinuumProperties`:

| Option | Method | Default | When to Add |
|--------|--------|---------|-------------|
| **Client Mode** | `setClientMode(boolean)` | `false` | API-only nodes (no data storage) |
| **Failure Detection Timeout** | `setFailureDetectionTimeout(long)` | `10000` ms | Network reliability tuning |
| **System Worker Blocked Timeout** | `setSystemWorkerBlockedTimeout(long)` | `null` | Detect deadlocks |
| **Metrics Log Frequency** | `setMetricsLogFrequency(long)` | `60000` ms | Metrics logging |
| **Network Timeout** | `setNetworkTimeout(long)` | `5000` ms | Global network timeout |
| **Public Thread Pool Size** | `setPublicThreadPoolSize(int)` | CPU count × 2 | Compute-heavy workloads |
| **System Thread Pool Size** | `setSystemThreadPoolSize(int)` | CPU count × 2 | Internal operations |
| **Message Queue Limit** | `setMessageQueueLimit(int)` | `1024` | High message volume |
| **Slow Client Queue Limit** | `setSlowClientQueueLimit(int)` | `0` (unlimited) | Slow client handling |

---

## Recommended Options to Add Next

Based on common production needs, consider exposing these options first:

### High Priority (Production Stability)

1. **Heartbeat Frequency** - For faster failure detection
   ```java
   private Long clusterHeartbeatFrequencyMs = 2000L;
   ```

2. **Network Timeout** - For high-latency networks
   ```java
   private Long clusterNetworkTimeoutMs = 5000L;
   ```

3. **Reconnect Count** - For unstable networks
   ```java
   private Integer clusterReconnectAttempts = 10;
   ```

4. **Failure Detection Timeout** - Global failure detection
   ```java
   private Long clusterFailureDetectionTimeoutMs = 10000L;
   ```

### Medium Priority (Performance Tuning)

5. **Connections Per Node** - For high throughput
   ```java
   private Integer clusterConnectionsPerNode = 1;
   ```

6. **Socket Write Timeout** - For slow networks
   ```java
   private Long clusterSocketWriteTimeoutMs = 2000L;
   ```

7. **Ack Timeout** - For geo-distributed clusters
   ```java
   private Long clusterAckTimeoutMs = 5000L;
   ```

### Low Priority (Advanced Scenarios)

8. **Client Mode** - For API-only nodes
   ```java
   private Boolean clusterClientMode = false;
   ```

9. **Label Selector** (K8s only) - For multi-cluster namespaces
   ```java
   private String clusterKubernetesLabelSelector = null;
   ```

10. **Connection Timeout** (K8s only) - For K8s API timeouts
    ```java
    private Integer clusterKubernetesConnectionTimeoutMs = 5000;
    ```

---

## Next Steps to Enable Kubernetes Discovery

1. **Add dependency to build.gradle**:
   ```gradle
   implementation 'org.apache.ignite:ignite-kubernetes'
   ```

2. **Uncomment code in `IgniteConfiguration.java`**:
   - Navigate to `createKubernetesIpFinder()` method
   - Uncomment the `TcpDiscoveryKubernetesIpFinder` implementation
   - Remove the fallback to `createLocalIpFinder()`

3. **Configure RBAC** (see RBAC section above)

4. **Deploy to Kubernetes**:
   ```bash
   helm install structures ./helm/structures \
     --set replicaCount=3 \
     --set properties.structures.clusterDiscoveryType=kubernetes \
     --set properties.structures.clusterKubernetesNamespace=production
   ```

5. **Verify cluster formation** in logs:
   ```
   INFO Apache Ignite started successfully - Cluster size: 3
   ```

---

## References

- [Apache Ignite Kubernetes IP Finder Documentation](https://ignite.apache.org/docs/latest/clustering/clustering#kubernetes-ip-finder)
- [Apache Ignite TCP Discovery SPI](https://ignite.apache.org/docs/latest/clustering/tcp-ip-discovery)
- [Kubernetes Headless Services](https://kubernetes.io/docs/concepts/services-networking/service/#headless-services)

---

## See Also

- **Quick Reference**: `IGNITE_CONFIGURATION_REFERENCE.md` - All current properties
- **Testing Guide**: `docker-compose/CLUSTER_TESTING.md` - How to test clusters
- **Design Doc**: `CACHE_EVICTION_DESIGN.md` - Architecture overview

---

**Last Updated**: January 2026  
**Total Tunable Options**: 30+ (12 implemented, 18+ available to add)  
**Apache Ignite Version**: Compatible with 2.x and 3.x  
**Configuration Prefix**: `continuum.cluster.*`

