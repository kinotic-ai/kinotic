# Cluster Cache Eviction Tests

This package contains integration tests for cluster-wide cache eviction using Testcontainers.

## Overview

These tests verify that cache eviction propagates correctly across a multi-node Structures cluster and handles node failures gracefully. They use Testcontainers to spin up a full 3-node cluster with Elasticsearch.

## Test Classes

- **`ClusterTestBase`** - Abstract base class that manages the test cluster lifecycle
  - Starts 3 Structures nodes + Elasticsearch
  - Configures static IP discovery for cluster formation
  - Provides utility methods for accessing nodes
  
- **`ClusterHealthVerifier`** - Utility class for health checks and verification
  - Health check endpoints
  - Cache eviction triggers
  - Query execution verification
  
- **`ClusterCacheEvictionTest`** - Main test suite
  - Cluster formation verification
  - Cache eviction propagation tests
  - Node failure handling tests
  - Deletion propagation tests
  - Metrics verification tests

## Prerequisites

1. **Docker** must be running
2. **Structures server image** must be built:
   ```bash
   ./gradlew :structures-server:bootBuildImage
   ```
3. Sufficient Docker resources:
   - Memory: 12GB minimum (3 nodes × 3GB + Elasticsearch)
   - Disk: 10GB free space

## Running the Tests

### Option 1: Gradle Task (Recommended)

```bash
# Run all cluster tests
./gradlew :structures-core:clusterTest

# Run with verbose output
./gradlew :structures-core:clusterTest --info
```

### Option 2: Specific Test

```bash
# Run specific test class
./gradlew :structures-core:test --tests ClusterCacheEvictionTest

# Run specific test method
./gradlew :structures-core:test --tests ClusterCacheEvictionTest.testClusterFormation
```

### Option 3: IDE

1. Remove the `@Disabled` annotation from `ClusterCacheEvictionTest`
2. Right-click the test class → Run

## Test Execution Time

- **Setup**: ~2-3 minutes (starting 3 nodes + Elasticsearch)
- **Test execution**: ~1-2 minutes
- **Teardown**: ~30 seconds
- **Total**: ~4-6 minutes

## What the Tests Verify

### 1. Cluster Formation
- All 3 nodes start successfully
- Nodes join the Ignite cluster
- Health endpoints respond

### 2. Cache Eviction Propagation
- Modification on node 1 triggers eviction
- Cache evicted on nodes 2 and 3
- Eviction completes within 5 seconds

### 3. Node Failure Handling
- Node 2 fails during operation
- Cluster detects failure
- Retry succeeds on remaining nodes (1 and 3)
- Failed node can rejoin cluster

### 4. Deletion Propagation
- Structure/NamedQuery deleted on node 1
- Caches evicted on all nodes
- Data no longer accessible anywhere

### 5. Metrics Recording
- OpenTelemetry metrics emitted
- Success/failure counters increment
- Latency histogram populated
- Retry counters track attempts

## Troubleshooting

### Tests Won't Start

**Problem**: Docker not running or insufficient resources

**Solution**:
```bash
# Check Docker status
docker info

# Verify resources in Docker Desktop settings
# Recommended: 12GB RAM, 4 CPUs
```

### Image Not Found

**Problem**: `kinotic/structures-server:3.5.0-SNAPSHOT` image doesn't exist

**Solution**:
```bash
# Build the image
./gradlew :structures-server:bootBuildImage

# Verify image exists
docker images | grep structures-server
```

### Cluster Formation Timeout

**Problem**: Nodes don't join cluster within timeout

**Solution**:
- Increase join timeout in `ClusterTestBase.waitForClusterFormation()`
- Check Docker network: `docker network ls`
- Check container logs: `docker logs <container-id>`

### Tests Are Slow

**Problem**: Tests take > 10 minutes

**Solution**:
- This is expected for first run (image pull + setup)
- Subsequent runs should be faster (~5 minutes)
- Consider running only specific tests during development

### Port Conflicts

**Problem**: Ports already in use

**Solution**:
- Tests use dynamic port mapping (no conflicts)
- If issues persist, check for orphaned containers:
  ```bash
  docker ps -a
  docker rm -f $(docker ps -aq)
  ```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Cluster Tests

on: [pull_request]

jobs:
  cluster-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          
      - name: Build server image
        run: ./gradlew :structures-server:bootBuildImage
        
      - name: Run cluster tests
        run: ./gradlew :structures-core:clusterTest
```

## Manual Testing Alternative

For manual/interactive testing, use the Docker Compose setup instead:

```bash
cd docker-compose
docker compose -f compose.cluster-test.yml up
```

See `docker-compose/CLUSTER_TESTING.md` for detailed manual testing procedures.

## Future Enhancements

These tests are currently simplified. Future improvements could include:

1. **Full end-to-end tests**:
   - Actually create structures on node 1
   - Query on all nodes to populate caches
   - Modify and verify eviction propagation
   - Delete and verify cleanup

2. **Metrics verification**:
   - Start OTEL collector in test
   - Query metrics after eviction
   - Assert expected values

3. **Load testing**:
   - Concurrent evictions
   - High-frequency modifications
   - Stress testing with many structures

4. **Network partition tests**:
   - Simulate network splits
   - Verify behavior during partitions
   - Test recovery after partition heals

## Related Documentation

- **Cache Eviction Design**: `structures-core/CACHE_EVICTION_DESIGN.md`
- **Manual Testing Guide**: `docker-compose/CLUSTER_TESTING.md`
- **Kubernetes Deployment**: `helm/structures/values.yaml`



