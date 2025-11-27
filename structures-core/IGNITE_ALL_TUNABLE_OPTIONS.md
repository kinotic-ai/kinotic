# Apache Ignite - Complete List of Tunable Options

This document provides a comprehensive list of ALL tunable options for Apache Ignite cluster configuration in Structures.

## Currently Implemented Options (via StructuresProperties)

### Core Configuration
| Property | Default | Type | Description |
|----------|---------|------|-------------|
| `cluster-discovery-type` | `"local"` | String | Discovery mechanism: `"local"`, `"sharedfs"`, `"kubernetes"` |
| `cluster-discovery-port` | `47500` | Integer | Port for discovery protocol |
| `cluster-communication-port` | `47100` | Integer | Port for node-to-node communication |
| `cluster-join-timeout-ms` | `30000` | Long | Timeout for joining cluster (ms) |

### Shared FS (Static IP) Discovery
| Property | Default | Type | Description |
|----------|---------|------|-------------|
| `cluster-shared-fs-addresses` | `"localhost:47500"` | String | Comma-separated list: `"host1:port1,host2:port2"` |

### Kubernetes Discovery
| Property | Default | Type | Description |
|----------|---------|------|-------------|
| `cluster-kubernetes-namespace` | `"default"` | String | K8s namespace for pod discovery |
| `cluster-kubernetes-service-name` | `"structures-ignite"` | String | Headless service name |
| `cluster-kubernetes-master-url` | `null` | String | K8s API server URL (optional, uses in-cluster if null) |
| `cluster-kubernetes-account-token` | `null` | String | Service account token (optional, uses mounted if null) |

### Cache Eviction Retry
| Property | Default | Type | Description |
|----------|---------|------|-------------|
| `max-cluster-sync-retry-attempts` | `3` | Integer | Max retry attempts for cluster eviction |
| `cluster-sync-retry-delay-ms` | `1000` | Long | Delay between retries (ms) |
| `cluster-sync-timeout-ms` | `30000` | Long | Timeout per sync attempt (ms) |

---

## Additional Options Available (Not Yet Exposed)

These options are available in Apache Ignite but not currently exposed via StructuresProperties. They can be added if needed.

### TcpDiscoveryKubernetesIpFinder Options

| Option | Method | Default | When to Add |
|--------|--------|---------|-------------|
| **Label Selector** | `setLabelSelector(String)` | `null` | Multiple clusters in same namespace |
| **Connection Timeout** | `setConnectionTimeout(int)` | `0` (no timeout) | Slow K8s API servers |
| **Read Timeout** | `setReadTimeout(int)` | `0` (no timeout) | Large clusters with slow API |
| **Shared** | `setShared(boolean)` | `false` | Multiple Ignite instances per JVM |

### TcpDiscoverySpi Options

| Option | Method | Default | When to Add |
|--------|--------|---------|-------------|
| **Network Timeout** | `setNetworkTimeout(long)` | `5000` ms | High latency networks |
| **Socket Timeout** | `setSocketTimeout(long)` | `5000` ms | Slow network connections |
| **Ack Timeout** | `setAckTimeout(long)` | `5000` ms | Geo-distributed clusters |
| **Max Ack Timeout** | `setMaxAckTimeout(long)` | `600000` ms | Heavy load scenarios |
| **Reconnect Count** | `setReconnectCount(int)` | `10` | Unstable networks |
| **Heartbeat Frequency** | `setHeartbeatFrequency(long)` | `2000` ms | Failure detection tuning |
| **Statistics Print Freq** | `setStatisticsPrintFrequency(long)` | `0` (disabled) | Debugging cluster issues |
| **Local Port Range** | `setLocalPortRange(int)` | `100` | Port conflicts |
| **IP Finder Clean Freq** | `setIpFinderCleanFrequency(long)` | `60000` ms | IP finder maintenance |

### TcpCommunicationSpi Options

| Option | Method | Default | When to Add |
|--------|--------|---------|-------------|
| **Connect Timeout** | `setConnectTimeout(long)` | `5000` ms | Slow connection establishment |
| **Max Connect Timeout** | `setMaxConnectTimeout(long)` | `600000` ms | Very slow networks |
| **Reconnect Count** | `setReconnectCount(int)` | `10` | Unstable connections |
| **Idle Connection Timeout** | `setIdleConnectionTimeout(long)` | `600000` ms | Connection pool management |
| **Socket Write Timeout** | `setSocketWriteTimeout(long)` | `2000` ms | Slow writes |
| **Socket Read Timeout** | `setSocketReadTimeout(long)` | `0` (no timeout) | Slow reads |
| **Connections Per Node** | `setConnectionsPerNode(int)` | `1` | High throughput requirements |
| **Local Port Range** | `setLocalPortRange(int)` | `100` | Port conflicts |
| **Shared Memory Port** | `setSharedMemoryPort(int)` | `48100` | Same-host optimization |
| **Message Queue Limit** | `setMessageQueueLimit(int)` | `1024` | High message volume |
| **Slow Client Queue Limit** | `setSlowClientQueueLimit(int)` | `0` (unlimited) | Slow client handling |

### IgniteConfiguration Options

| Option | Method | Default | When to Add |
|--------|--------|---------|-------------|
| **Client Mode** | `setClientMode(boolean)` | `false` | API-only nodes (no data storage) |
| **Failure Detection Timeout** | `setFailureDetectionTimeout(long)` | `10000` ms | Network reliability tuning |
| **System Worker Blocked Timeout** | `setSystemWorkerBlockedTimeout(long)` | `null` | Detect deadlocks |
| **Metrics Log Frequency** | `setMetricsLogFrequency(long)` | `60000` ms | Metrics logging |
| **Network Timeout** | `setNetworkTimeout(long)` | `5000` ms | Global network timeout |
| **Public Thread Pool Size** | `setPublicThreadPoolSize(int)` | CPU count * 2 | Compute-heavy workloads |
| **System Thread Pool Size** | `setSystemThreadPoolSize(int)` | CPU count * 2 | Internal operations |

---

## How to Add New Options

To expose additional tuning options, follow this pattern:

### Step 1: Add to StructuresProperties

```java
/**
 * Heartbeat frequency for cluster node health checks (milliseconds)
 * Lower = faster failure detection, higher network traffic
 * Higher = slower failure detection, lower network traffic
 */
private Long clusterHeartbeatFrequencyMs = 2000L;
```

### Step 2: Use in IgniteConfiguration

```java
// In createDiscoverySpi() method
if (properties.getClusterHeartbeatFrequencyMs() != null) {
    discoverySpi.setHeartbeatFrequency(properties.getClusterHeartbeatFrequencyMs());
}
```

### Step 3: Document in application.yml

```yaml
structures:
  cluster-heartbeat-frequency-ms: ${STRUCTURES_CLUSTER_HEARTBEAT_FREQUENCY_MS:2000}
```

### Step 4: Update Helm values.yaml

```yaml
properties:
  structures:
    clusterHeartbeatFrequencyMs: 2000
```

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

## Configuration Matrix by Environment

### Development (Single-Node)
```yaml
structures:
  cluster-discovery-type: "local"
  # No other cluster properties needed
```

### Docker Compose (Testing)
```yaml
structures:
  cluster-discovery-type: "sharedfs"
  cluster-shared-fs-addresses: "node1:47500,node2:47500,node3:47500"
  cluster-discovery-port: 47500
  cluster-communication-port: 47100
  cluster-join-timeout-ms: 30000
```

### Kubernetes Production (Minimal)
```yaml
structures:
  cluster-discovery-type: "kubernetes"
  cluster-kubernetes-namespace: "production"
  cluster-kubernetes-service-name: "structures-ignite"
  cluster-discovery-port: 47500
  cluster-communication-port: 47100
  cluster-join-timeout-ms: 60000
```

### Kubernetes Production (Optimized for Fast Failure Detection)
```yaml
structures:
  cluster-discovery-type: "kubernetes"
  cluster-kubernetes-namespace: "production"
  cluster-kubernetes-service-name: "structures-ignite"
  cluster-discovery-port: 47500
  cluster-communication-port: 47100
  cluster-join-timeout-ms: 60000
  # Additional (requires adding to StructuresProperties):
  cluster-heartbeat-frequency-ms: 1000  # Fast detection
  cluster-network-timeout-ms: 3000
  cluster-reconnect-attempts: 5
```

### Kubernetes Production (Geo-Distributed)
```yaml
structures:
  cluster-discovery-type: "kubernetes"
  cluster-kubernetes-namespace: "production"
  cluster-kubernetes-service-name: "structures-ignite"
  cluster-discovery-port: 47500
  cluster-communication-port: 47100
  cluster-join-timeout-ms: 120000  # Longer for latency
  # Additional (requires adding to StructuresProperties):
  cluster-heartbeat-frequency-ms: 5000  # Less frequent
  cluster-network-timeout-ms: 15000     # Higher tolerance
  cluster-ack-timeout-ms: 15000
  cluster-socket-write-timeout-ms: 10000
```

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

## Next Steps to Enable Kubernetes Discovery

1. **Add dependency to build.gradle**:
   ```gradle
   implementation 'org.apache.ignite:ignite-kubernetes'
   ```

2. **Uncomment code in `IgniteConfiguration.java`**:
   - Navigate to `createKubernetesIpFinder()` method
   - Uncomment the `TcpDiscoveryKubernetesIpFinder` implementation
   - Remove the fallback to `createLocalIpFinder()`

3. **Configure RBAC** (see RBAC section in this document)

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

## See Also

- **Quick Reference**: `IGNITE_CONFIGURATION_REFERENCE.md` - All current properties
- **Kubernetes Tuning**: `IGNITE_KUBERNETES_TUNING.md` - Detailed K8s options
- **Testing Guide**: `docker-compose/CLUSTER_TESTING.md` - How to test clusters
- **Design Doc**: `CACHE_EVICTION_DESIGN.md` - Architecture overview

---

**Last Updated**: February 13, 2025  
**Total Tunable Options**: 30+ (12 implemented, 18+ available to add)  
**Configuration System**: StructuresProperties (Spring Boot ConfigurationProperties)














