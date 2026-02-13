# Cache Eviction Design and Implementation

## Overview

This document outlines the design and implementation of cache eviction in the Structures platform, covering both local cache management and future cluster-wide coordination.

## Problem Statement

The Structures platform uses multiple local caches (Caffeine) for performance optimization:
- **Entity Service Cache**: Caches entity services by structure ID
- **GraphQL Handler Cache**: Caches GraphQL handlers by application ID  
- **GQL Operation Definition Cache**: Caches GraphQL operation definitions

When data changes occur (structure updates, named query modifications), these caches need to be evicted with proper ordering to maintain consistency.

## Key Challenges

1. **Circular Dependencies**: Cache eviction service needs to call multiple services that might depend on each other
2. **Ordering Requirements**: Cache eviction must happen in a specific sequence
3. **Future Cluster Support**: Need to support cluster-wide eviction later
4. **Local vs Cluster Operations**: Different execution paths for local changes vs cluster messages

## Current Implementation: Hybrid Event-Driven Architecture

### Current Architecture

```
Local API Call                          Cluster Message
     │                                        │
     ▼                                        ▼
┌─────────────────────────────────────────────────────────┐
│ DefaultCacheEvictionService                             │
│                                                         │
│ evictCachesFor(structure) ──┐    ┌── @EventListener     │
│ evictCachesFor(namedQuery) ─┼────┼── handleStructure... │
│                             │    │                     │
│                             ▼    ▼                     │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ Private Methods (Shared Core Logic)                 │ │
│ │ - evictStructure(structure)                         │ │
│ │ - evictNamedQuery(namedQuery)                       │ │
│ └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────┐
│ Ordered Cache Service Calls                             │
│ 1. EntitiesService.evictCachesFor(structure)           │
│ 2. GqlOperationDefinitionService.evictCachesFor(...)   │
│ 3. DelegatingGqlHandler.evictCachesFor(structure)      │
└─────────────────────────────────────────────────────────┘
```

## Implementation Details

### Cache Eviction Service

The `DefaultCacheEvictionService` handles cache eviction with proper ordering and cluster propagation:

```java
@Component
public class DefaultCacheEvictionService implements CacheEvictionService {

    // Clean dependencies - no circular issues
    private final EntitiesService entitiesService;
    private final GqlOperationDefinitionService gqlOperationDefinitionService;
    private final DelegatingGqlHandler delegatingGqlHandler;
    private final StructureDAO structureDAO;

    // Public API - performs local eviction + broadcasts to cluster
    public void evictCachesFor(Structure structure) {
        evictStructure(structure);           // Local eviction
        broadcastToCluster(structure);       // Cluster-wide eviction via Ignite Compute Grid
    }

    public void evictCachesFor(NamedQueriesDefinition namedQuery) {
        evictNamedQuery(namedQuery);         // Local eviction
        broadcastToCluster(namedQuery);      // Cluster-wide eviction via Ignite Compute Grid
    }

    // Event listeners for cluster messages (received from other nodes)
    @EventListener
    public void handleStructureCacheEviction(StructureCacheEvictionEvent event) {
        evictStructure(event.getStructure());
    }

    @EventListener  
    public void handleNamedQueryCacheEviction(NamedQueryCacheEvictionEvent event) {
        evictNamedQuery(event.getNamedQuery());
    }

    // Private methods with proper ordering
    private void evictStructure(Structure structure) {
        entitiesService.evictCachesFor(structure);              // Step 1
        gqlOperationDefinitionService.evictCachesFor(structure); // Step 2
        delegatingGqlHandler.evictCachesFor(structure);          // Step 3
    }

    private void evictNamedQuery(NamedQueriesDefinition namedQuery) {
        String structureId = StructuresUtil.structureNameToId(namedQuery.getApplicationId(), namedQuery.getStructure());
        structureDAO.findById(structureId)
                    .thenAccept(structure -> {
                        gqlOperationDefinitionService.evictCachesFor(structure);
                        delegatingGqlHandler.evictCachesFor(structure);
                    }).join();
    }
}
```

### Critical Cache Eviction Ordering

**Structure Cache Eviction Order (Required)**:
1. **EntitiesService** - Must be first (core entity data and metadata)
2. **GqlOperationDefinitionService** - Second (depends on entity metadata)  
3. **DelegatingGqlHandler** - Last (compiled handlers depend on operation definitions)

**Named Query Cache Eviction Process**:
1. **Structure Lookup** - Resolve NamedQuery → Structure
2. **GqlOperationDefinitionService** - Clear operation definitions
3. **DelegatingGqlHandler** - Clear compiled handlers

## Cluster Integration: Ignite Compute Grid ✅ IMPLEMENTED

### Current Implementation
Cluster-wide cache eviction is now implemented using Apache Ignite Compute Grid:

```java
@Override
public void evictCachesFor(Structure structure) {
    evictStructure(structure);                    // ← Local eviction
    
    // Cluster eviction using Ignite Compute Grid with validation
    try {
        Ignite ignite = Ignition.ignite();
        ClusterGroup servers = ignite.cluster().forServers();
        
        // Broadcast to all server nodes and collect results
        IgniteFuture<Void> future = ignite.compute(servers).broadcastAsync(new ClusterCacheEvictionTask("STRUCTURE", structure.getId()));
        future.get(); // Wait for completion and throw exception if any node failed
        
        log.info("Structure cache eviction successfully completed on all {} cluster nodes for: {}", 
                servers.nodes().size(), structure.getId());
    } catch (Exception e) {
        log.error("Failed to complete structure cache eviction on cluster for: {}", structure.getId(), e);
    }
}

// Simple compute task that publishes events on remote nodes
public class ClusterCacheEvictionTask implements IgniteRunnable {
    @SpringResource(resourceClass = ApplicationEventPublisher.class)
    private ApplicationEventPublisher eventPublisher;
    
    private final EvictionSourceType evictionSourceType; // STRUCTURE or NAMED_QUERY
    private final EvictionSourceOperation evictionOperation; // MODIFY or DELETE
    private final String applicationId;
    private final String structureId;
    private final String namedQueryId;
    private final long timestamp; // Timestamp for versioning and logging
    
    @Override
    public void run() {
        // Check for duplicate processing using auto-expiring cache
        String evictionKey = buildEvictionKey();
        if (processedEvictions.getIfPresent(evictionKey) != null) {
            return; // Skip duplicate processing
        }
        
        // Publish appropriate event based on type and operation
        if (evictionSourceType == STRUCTURE) {
            if (evictionOperation == MODIFY) {
                eventPublisher.publishEvent(
                    CacheEvictionEvent.clusterModifiedStructure(applicationId, structureId));
            } else {
                eventPublisher.publishEvent(
                    CacheEvictionEvent.clusterDeletedStructure(applicationId, structureId));
            }
        } else {
            if (evictionOperation == MODIFY) {
                eventPublisher.publishEvent(
                    CacheEvictionEvent.clusterModifiedNamedQuery(applicationId, structureId, namedQueryId));
            } else {
                eventPublisher.publishEvent(
                    CacheEvictionEvent.clusterDeletedNamedQuery(applicationId, structureId, namedQueryId));
            }
        }
        
        // Mark as processed (auto-expires after 1 hour)
        processedEvictions.put(evictionKey, timestamp);
    }
}
```

### Why Ignite Compute Grid?
- **Built-in Acknowledgments**: Know which nodes succeeded/failed
- **Automatic Timeout Handling**: No manual timeout logic needed
- **Failure Detection**: Automatic detection of non-responding nodes
- **Simple Implementation**: Clean, straightforward code
- **Already Available**: Uses existing Ignite dependency
- **Concise**: Single task handles both Structure and NamedQuery eviction
- **Serialization-Safe**: Uses IDs instead of full objects to avoid serialization issues
- **Retry-Enabled**: Automatic retry with configurable attempts and timeouts
- **Duplicate Prevention**: Caffeine-based deduplication with automatic expiry

### Retry Configuration
```java
// Retry configuration for cluster cache eviction
private static final int MAX_RETRY_ATTEMPTS = 3;
private static final long RETRY_DELAY_MS = 1000; // 1 second
private static final long CLUSTER_TIMEOUT_MS = 30000; // 30 seconds
```

**Retry Logic**:
- **3 Attempts**: Up to 3 retry attempts for failed cluster operations
- **1 Second Delay**: 1 second delay between retry attempts
- **30 Second Timeout**: 30 second timeout per cluster operation
- **Consistent Timestamp**: Same timestamp used across all retry attempts for proper versioning
- **Exponential Backoff**: Future enhancement for more sophisticated retry timing

**Versioning Benefits**:
- **Deduplication**: Downstream nodes can identify and skip duplicate requests
- **Consistency**: All retry attempts represent the same logical eviction request
- **Audit Trail**: Clear correlation between retry attempts and the original request
- **Idempotency**: Safe to retry without side effects

## Benefits of Current Design

✅ **No Circular Dependencies**: Clean dependency injection  
✅ **Guaranteed Ordering**: Critical cache eviction sequence enforced
✅ **Code Reuse**: Shared logic between local and cluster operations
✅ **Simple**: Minimal complexity, easy to understand
✅ **Testable**: Easy to test ordering and error scenarios
✅ **Cluster-Complete**: Full cluster-wide cache eviction using Ignite Compute Grid
✅ **Cluster-Validated**: Ensures all server nodes successfully process eviction requests
✅ **Retry-Logic**: Automatic retry with configurable attempts for failed cluster operations
✅ **Duplicate-Prevention**: Caffeine-based deduplication with automatic 1-hour expiry
✅ **Concise**: Single compute task handles both Structure and NamedQuery eviction
✅ **Resilient**: Graceful handling of cluster communication failures
✅ **Delete Support**: Handles both modification and deletion operations

## Usage Examples

### Structure Updates
```java
// When a structure is modified
cacheEvictionService.evictCachesFor(structure);
// → Executes ordered local eviction
// → Broadcasts eviction to all cluster nodes via Ignite Compute Grid
```

### Named Query Updates  
```java
// When a named query is modified
cacheEvictionService.evictCachesFor(namedQuery);
// → Looks up structure
// → Executes ordered eviction
// → Broadcasts eviction to all cluster nodes via Ignite Compute Grid
```

## OpenTelemetry Metrics

The cache eviction system exposes the following metrics for monitoring and alerting:

### Available Metrics

| Metric Name | Type | Description | Attributes |
|-------------|------|-------------|------------|
| `cache.eviction.requests` | Counter | Total cache eviction requests received | `eviction.type`, `eviction.operation`, `eviction.source` |
| `cache.eviction.cluster.results` | Counter | Cluster eviction results (success/failure) | `eviction.type`, `eviction.operation`, `result`, `attempts` |
| `cache.eviction.cluster.duration` | Histogram | Cluster eviction duration in milliseconds | `eviction.type`, `eviction.operation`, `result`, `attempts` |
| `cache.eviction.cluster.retries` | Counter | Number of retry attempts for cluster evictions | `eviction.type`, `eviction.operation` |

### Metric Attributes

- **eviction.type**: `STRUCTURE` or `NAMED_QUERY`
- **eviction.operation**: `MODIFY` or `DELETE`
- **eviction.source**: `LOCAL_MESSAGE` or `CLUSTER_MESSAGE`
- **result**: `success` or `failure`
- **attempts**: Number of attempts (e.g., "1", "2", "3")

### Example Prometheus Queries

```promql
# Cache eviction request rate by type
rate(cache_eviction_requests_total[5m])

# Cluster eviction success rate
rate(cache_eviction_cluster_results_total{result="success"}[5m]) 
  / 
rate(cache_eviction_cluster_results_total[5m])

# Average cluster eviction duration
rate(cache_eviction_cluster_duration_sum[5m]) 
  / 
rate(cache_eviction_cluster_duration_count[5m])

# Retry rate (should be low)
rate(cache_eviction_cluster_retries_total[5m])

# P95 cluster eviction latency
histogram_quantile(0.95, 
  rate(cache_eviction_cluster_duration_bucket[5m]))
```

### Recommended Alerts

```yaml
# Alert if cluster eviction failure rate > 5%
- alert: CacheEvictionHighFailureRate
  expr: |
    (
      rate(cache_eviction_cluster_results_total{result="failure"}[5m])
      /
      rate(cache_eviction_cluster_results_total[5m])
    ) > 0.05
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High cache eviction failure rate"

# Alert if cluster eviction latency > 5s (P95)
- alert: CacheEvictionSlowPerformance
  expr: |
    histogram_quantile(0.95,
      rate(cache_eviction_cluster_duration_bucket[5m])
    ) > 5000
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Slow cache eviction performance"

# Alert if retry rate is high (> 10% of requests)
- alert: CacheEvictionHighRetryRate
  expr: |
    (
      rate(cache_eviction_cluster_retries_total[5m])
      /
      rate(cache_eviction_requests_total[5m])
    ) > 0.10
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High cache eviction retry rate"
```

## Cluster Testing

The cache eviction system includes comprehensive cluster testing capabilities to verify that cache eviction propagates correctly across all nodes.

### Testing Approaches

**1. Automated Testcontainers Tests** (CI/CD)

Located in `structures-core/src/test/java/org/kinotic/structures/cluster/`:

- `ClusterCacheEvictionTest` - Main integration test suite
- `ClusterTestBase` - Base class for cluster test setup
- `ClusterHealthVerifier` - Utilities for health checks and verification

To run automated cluster tests:
```bash
./gradlew :structures-server:bootBuildImage  # Build image first
./gradlew :structures-core:test --tests ClusterCacheEvictionTest
```

**2. Manual Docker Compose Testing** (Pre-release validation)

Use the 3-node cluster configuration for manual testing:

```bash
cd docker-compose
docker compose -f compose.cluster-test.yml up
```

See `docker-compose/CLUSTER_TESTING.md` for detailed testing procedures.

### Test Scenarios

**Automated Tests**:
- `testClusterFormation()` - Verify all nodes start and join cluster
- `testCacheEvictionPropagatesAcrossCluster()` - Modify on node1, verify eviction on node2/node3
- `testNodeFailureHandling()` - Kill node during eviction, verify retry succeeds
- `testDeletionPropagation()` - Delete structure/named query, verify cluster-wide eviction
- `testMetricsRecorded()` - Verify OpenTelemetry metrics are emitted correctly

**Manual Test Procedures**:
1. Structure modification propagation
2. Structure deletion propagation  
3. Named query modification propagation
4. Node failure during eviction
5. Metrics verification in Grafana
6. Log verification across nodes

### Cluster Configuration

**Docker/Testcontainers** (Static IP Discovery):
```yaml
kinotic:
  disableClustering: false
  cluster:
    discoveryType: SHAREDFS
    localAddresses: "node1:47500,node2:47500,node3:47500"
    discoveryPort: 47500
    communicationPort: 47100
```

**Kubernetes** (Kubernetes Discovery):
```yaml
kinotic:
  disableClustering: false
  cluster:
    discoveryType: KUBERNETES
    kubernetesNamespace: structures
    kubernetesServiceName: structures-ignite
    discoveryPort: 47500
    communicationPort: 47100
```

### Performance Expectations

**Normal Operation**:
- Cache eviction completes in < 1 second
- Retry rate < 1%
- Success rate > 99%
- P95 latency < 2 seconds

**During Node Failure**:
- First attempt fails to unreachable node
- Retry succeeds within 1-2 seconds (refreshed topology excludes dead node)
- Total duration < 5 seconds
- Cluster continues operating with remaining nodes

### Troubleshooting

**Nodes not joining cluster**:
- Check Docker network connectivity
- Verify discovery addresses in configuration (`kinotic.cluster.localAddresses`)
- Increase `kinotic.cluster.joinTimeoutMs` if nodes are slow
- Check logs for "Topology snapshot" messages

**Cache eviction not propagating**:
- Verify cluster topology shows all nodes
- Check network connectivity between containers
- Look for "cache eviction failed" in logs
- Increase retry attempts or timeout

**High retry rate**:
- Check network latency between nodes
- Increase cluster sync timeout
- Verify node health and resource usage
- Look for node restarts in logs

See `docker-compose/CLUSTER_TESTING.md` for complete troubleshooting guide.

## Kubernetes Production Deployment

### Discovery Configuration

For production Kubernetes deployments, use Kubernetes discovery instead of static IP:

**Application Configuration** (via environment variables or ConfigMap):
```yaml
kinotic:
  disableClustering: false
  cluster:
    discoveryType: KUBERNETES
    kubernetesNamespace: structures
    kubernetesServiceName: structures-ignite
    discoveryPort: 47500
    communicationPort: 47100

# Or as environment variables:
# CONTINUUM_CLUSTER_DISCOVERY_TYPE: KUBERNETES
# CONTINUUM_CLUSTER_KUBERNETES_NAMESPACE: structures
# CONTINUUM_CLUSTER_KUBERNETES_SERVICE_NAME: structures-ignite
```

**Headless Service** (for Ignite discovery):
```yaml
apiVersion: v1
kind: Service
metadata:
  name: structures-ignite
spec:
  clusterIP: None  # Headless service
  selector:
    app: structures
  ports:
    - name: discovery
      port: 47500
    - name: communication
      port: 47100
```

**StatefulSet** (for stable network identities):
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: structures
spec:
  serviceName: structures-ignite
  replicas: 3  # Recommended minimum for fault tolerance
  selector:
    matchLabels:
      app: structures
  template:
    spec:
      containers:
      - name: structures
        env:
        - name: CONTINUUM_CLUSTER_DISCOVERY_TYPE
          value: "KUBERNETES"
        - name: CONTINUUM_CLUSTER_KUBERNETES_NAMESPACE
          value: "structures"
        - name: CONTINUUM_CLUSTER_KUBERNETES_SERVICE_NAME
          value: "structures-ignite"
```

### Production Recommendations

**Cluster Sizing**:
- Minimum: 3 nodes (provides fault tolerance)
- Recommended: 3-5 nodes (balances redundancy and complexity)
- Large deployments: 5-7 nodes (higher availability)

**Resource Requirements** (per node):
- CPU: 2-4 cores
- Memory: 4-8 GB
- Disk: SSD for best Elasticsearch performance

**Monitoring**:
- Set up alerts for cache eviction failure rate > 5%
- Monitor P95 latency < 5 seconds
- Alert on retry rate > 10%
- Track cluster size changes

**High Availability**:
- Deploy nodes across availability zones
- Use pod anti-affinity rules
- Configure proper liveness/readiness probes
- Set up PodDisruptionBudgets

---
**Document Version**: 6.0  
**Last Updated**: January 2026  
**Status**: ✅ COMPLETE - Local and cluster-wide cache eviction with OpenTelemetry metrics and cluster testing
