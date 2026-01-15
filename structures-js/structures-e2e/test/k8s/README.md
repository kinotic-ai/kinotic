# Kubernetes Integration Tests

## Overview

These tests verify cache eviction propagation across a Kubernetes cluster by connecting to multiple pods via the Continuum WebSocket/STOMP protocol. The tests also verify evictions through CSV files written by the `EvictionEventRecorder`.

## Prerequisites

1. **Kubernetes cluster running** (KinD or any K8s cluster)
2. **structures-server deployed** with 3 replicas
3. **kubectl configured** and cluster accessible
4. **Node.js and npm** installed
5. **Eviction tracking enabled** in helm values (`evictionTracking.enabled: true`)

## Quick Start

```bash
# 1. Create KinD cluster
cd ../../  # Navigate to project root
./dev-tools/kind/kind-cluster.sh create

# 4. Deploy with 3 replicas
./dev-tools/kind/kind-cluster.sh deploy

# 5. Run K8s tests
cd structures-js/structures-e2e
K8S_TEST_ENABLED=true npm test -- k8s-cache-eviction.test.ts
```

## Configuration

Set these environment variables to configure the tests:

| Variable | Description | Default |
|----------|-------------|---------|
| `K8S_TEST_ENABLED` | Enable K8s tests | `false` |
| `K8S_CONTEXT` | Kubernetes context | `kind-structures-cluster` |
| `K8S_NAMESPACE` | Kubernetes namespace | `default` |
| `K8S_LABEL_SELECTOR` | Pod label selector | `app=structures` |
| `K8S_REPLICA_COUNT` | Expected replicas | `3` |
| `K8S_STOMP_PORT` | Continuum STOMP port in pods | `58503` |
| `K8S_STARTING_LOCAL_PORT` | Starting local port for port-forwards | `58511` |
| `K8S_EVICTION_DATA_PATH` | Path to eviction CSV files | `../../dev-tools/kind/eviction-data` |

### Example .env file

Create a `.env.k8s` file:

```bash
K8S_TEST_ENABLED=true
K8S_CONTEXT=kind-structures-cluster
K8S_NAMESPACE=default
K8S_LABEL_SELECTOR=app=structures
K8S_REPLICA_COUNT=3
K8S_STOMP_PORT=58503
K8S_STARTING_LOCAL_PORT=58511
K8S_EVICTION_DATA_PATH=../../dev-tools/kind/eviction-data
```

Then run:
```bash
source .env.k8s && npm test -- k8s-cache-eviction.test.ts
```

## Test Architecture

### What It Tests

The tests verify that when a Structure is modified on one pod:
1. Cache eviction events propagate to all pods via Apache Ignite Compute Grid
2. Eviction events are recorded to CSV files by each pod
3. No stale data is served after eviction propagates

### Test 1: Basic Cache Eviction with Entity Operations

This test establishes the fundamental cache eviction verification:

1. **Setup** - Clear eviction files, discover pods, create port-forwards
2. **Create Structure** - Create a Vehicle structure on pod 0
3. **Create Entities** - Save test vehicles to populate entity data
4. **Warm Caches** - Call `findAll()` and `search()` on each pod to populate caches
5. **Modify Structure** - Update structure on pod 0 (triggers eviction)
6. **Verify CSV Files** - Check eviction records in `evictions-{pod}.csv`
7. **Verify Consistency** - Confirm all pods see the updated Structure

### Test 2: Concurrent Operations (Load Test Foundation)

This test provides a foundation for load testing and HA scenarios:

1. **Setup** - Same as Test 1 but with more entities (10 vehicles)
2. **Warm Caches** - Populate caches on all pods
3. **Concurrent Operations** - Rapid reads during structure modification
4. **Track Performance** - Record timing metrics for each operation
5. **Verify No Stale Data** - Ensure final reads all see updated data
6. **Report Metrics** - Print performance summary

### Port Forwarding

The test automatically creates `kubectl port-forward` processes for each pod:
- Pod 0: `localhost:58511` → `pod:58503`
- Pod 1: `localhost:58512` → `pod:58503`
- Pod 2: `localhost:58513` → `pod:58503`

Each test connects/disconnects to pods as needed to verify behavior.

## Eviction Tracking Verification

### How It Works

The structures-server writes eviction events to CSV files when the `eviction-tracking` Spring profile is active. Each pod writes to its own file:

```
/eviction-data/evictions-{pod-name}.csv
```

The KinD cluster mounts `./dev-tools/kind/eviction-data` from your host machine, so the tests can read these files directly.

### CSV File Format

```csv
timestamp,cacheName,key,cause
1704384000000,structureCache,myapp.vehicle,EXPLICIT
1704384001000,entityCache,myapp.vehicle:abc-123,EXPLICIT
```

Fields:
- `timestamp` - Unix timestamp in milliseconds
- `cacheName` - Name of the Caffeine cache
- `key` - The cache key that was evicted
- `cause` - Removal cause: `EXPLICIT`, `REPLACED`, `EXPIRED`, `SIZE`, `COLLECTED`

### Eviction Causes

| Cause | Description |
|-------|-------------|
| `EXPLICIT` | `cache.invalidate()` was called (structure modification) |
| `REPLACED` | Entry was replaced with a new value |
| `EXPIRED` | Entry expired due to TTL or access-based expiration |
| `SIZE` | Evicted due to cache size limits |
| `COLLECTED` | Garbage collected (weak/soft references) |

### Utility Functions

The `eviction-utils.ts` module provides helpers for working with eviction data:

```typescript
import { 
    readEvictionFiles,
    waitForEvictions,
    summarizeEvictions,
    filterByStructureId,
    clearEvictionFiles 
} from './eviction-utils';

// Read all eviction files
const evictions = readEvictionFiles('/path/to/eviction-data');

// Wait for evictions with filtering
const evictions = await waitForEvictions({
    basePath: '/path/to/eviction-data',
    minRecords: 3,
    timeout: 30000,
    sinceTimestamp: Date.now() - 60000 // Last minute
});

// Get summary statistics
const summary = summarizeEvictions(evictions);
console.log(summary.totalRecords, summary.byCause, summary.byPod);

// Filter by structure
const structureEvictions = filterByStructureId(evictions, 'myapp.vehicle');
```

## Manual Testing

You can manually test cache eviction with these commands:

```bash
# Terminal 1: Port forward to pod 0
kubectl --context kind-structures-cluster -n default port-forward \
  $(kubectl --context kind-structures-cluster -n default get pods -l app=structures -o jsonpath='{.items[0].metadata.name}') \
  58511:58503

# Terminal 2: Port forward to pod 1
kubectl --context kind-structures-cluster -n default port-forward \
  $(kubectl --context kind-structures-cluster -n default get pods -l app=structures -o jsonpath='{.items[1].metadata.name}') \
  58512:58503

# Terminal 3: Port forward to pod 2
kubectl --context kind-structures-cluster -n default port-forward \
  $(kubectl --context kind-structures-cluster -n default get pods -l app=structures -o jsonpath='{.items[2].metadata.name}') \
  58513:58503

# Terminal 4: Run the test
cd structures-js/structures-e2e
K8S_TEST_ENABLED=true npm test -- k8s-cache-eviction.test.ts
```

### Checking Eviction Files

```bash
# View eviction data directory
ls -la dev-tools/kind/eviction-data/

# View evictions from a specific pod
cat dev-tools/kind/eviction-data/evictions-structures-0.csv

# Watch for new evictions in real-time
tail -f dev-tools/kind/eviction-data/evictions-*.csv
```

## Troubleshooting

### Test Skipped

If tests are skipped:
```bash
# Verify K8S_TEST_ENABLED is set
echo $K8S_TEST_ENABLED

# Verify cluster is accessible
kubectl --context kind-structures-cluster cluster-info

# Verify pods are running
kubectl --context kind-structures-cluster -n default get pods -l app=structures
```

### Port Forward Issues

If port forwarding fails:
```bash
# Check for port conflicts
lsof -i :58511
lsof -i :58512
lsof -i :58513

# Clean up old port-forwards
pkill -f "kubectl port-forward"
```

### No Eviction CSV Files

If eviction CSV files are not being created:

1. **Verify eviction tracking is enabled** in helm values:
   ```yaml
   evictionTracking:
     enabled: true
   ```

2. **Check Spring profile is active**:
   ```bash
   kubectl logs <pod-name> | grep "eviction-tracking"
   ```

3. **Verify mount paths**:
   ```bash
   kubectl exec <pod-name> -- ls -la /eviction-data/
   ```

4. **Check host mount in KinD**:
   ```bash
   ls -la dev-tools/kind/eviction-data/
   ```

### Cache Eviction Not Propagating

If cache eviction doesn't propagate:

1. **Check Ignite cluster health** in pod logs:
   ```bash
   kubectl --context kind-structures-cluster logs <pod-name> | grep "Topology snapshot"
   ```
   Should show 3 nodes in the cluster.

2. **Increase wait time** - Try changing the 5-second wait to 10 seconds in the test

3. **Check network policies** - Ensure pods can communicate:
   ```bash
   kubectl --context kind-structures-cluster get networkpolicies
   ```

4. **Verify Ignite discovery** - Check that all pods discovered each other:
   ```bash
   kubectl --context kind-structures-cluster logs <pod-name> | grep "TcpDiscoverySpi"
   ```

## Extending for Load Testing

The second test (`should handle concurrent entity operations during cache eviction`) provides a foundation for load testing. To extend it:

### Increase Concurrency

Modify the test to run more iterations or parallel operations:

```typescript
// More vehicles for higher load
const testVehicles = createTestVehicles(100);

// More read iterations
for (let round = 0; round < 10; round++) {
    // ... read operations
}
```

### Track Custom Metrics

Add custom timing metrics:

```typescript
const timings: { operation: string; durationMs: number }[] = [];

const trackTiming = (operation: string, startTime: number) => {
    timings.push({ operation, durationMs: Date.now() - startTime });
};
```

### Test HA Scenarios

Future extensions could include:
- Killing pods during operations (`kubectl delete pod`)
- Introducing network partitions
- Testing failover behavior

## CI/CD Integration

### GitHub Actions Example

```yaml
name: K8s Tests

on: [push, pull_request]

jobs:
  k8s-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup KinD
        uses: helm/kind-action@v1
        with:
          cluster_name: structures-cluster
      
      - name: Build and Deploy
        run: |
          ./gradlew :structures-server:bootBuildImage
          kind load docker-image structures-server:latest
          ./dev-tools/kind/kind-cluster.sh deploy
      
      - name: Run K8s Tests
        working-directory: structures-js/structures-e2e
        env:
          K8S_TEST_ENABLED: true
        run: npm test -- k8s-cache-eviction.test.ts
      
      - name: Upload Eviction Data
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: eviction-data
          path: dev-tools/kind/eviction-data/
```

## Benefits

- **True integration testing** - Tests actual deployed cluster behavior
- **Uses real Continuum client** - Same client applications use
- **No mocking** - Tests real Ignite cache eviction
- **Verifies multi-pod behavior** - Ensures cluster-wide consistency
- **CSV verification** - Confirms eviction events are recorded
- **Performance baseline** - Tracks timing metrics for load testing
- **Easy to debug** - Can inspect pod logs and CSV files independently
- **CI/CD ready** - Can run in automated pipelines

## Related Documentation

- [../../KUBERNETES_CACHE_EVICTION_TESTS.md](../../KUBERNETES_CACHE_EVICTION_TESTS.md) - Original requirements
- [../../docker-compose/CLUSTER_TESTING.md](../../docker-compose/CLUSTER_TESTING.md) - Docker Compose cluster testing
- [../../dev-tools/kind/README.md](../../dev-tools/kind/README.md) - KinD cluster setup
- [structures-auth/EvictionEventRecorder.java](../../../structures-auth/src/main/java/org/mindignited/structures/auth/internal/services/EvictionEventRecorder.java) - CSV recorder implementation
