# Cluster Cache Eviction Testing Guide

This guide explains how to manually test cluster-wide cache eviction using Docker Compose.

## Prerequisites

1. Docker and Docker Compose installed
2. Structures server image built: `./gradlew :structures-server:bootBuildImage`
3. At least 12GB RAM available for Docker (3 nodes × 3GB + Elasticsearch + observability stack)

## Starting the Cluster

### 1. Start the 3-node cluster

```bash
cd docker-compose
docker compose -f compose.cluster-test.yml up
```

This will start:
- Elasticsearch (shared by all nodes)
- 3 Structures server nodes (node1, node2, node3)
- OpenTelemetry collector
- Grafana + Prometheus (for metrics)
- Jaeger (for traces)
- Loki (for logs)

### 2. Verify cluster formation

Watch the logs for all nodes to join the Ignite cluster. You should see messages like:

```
Topology snapshot [ver=3, servers=3, clients=0, ...]
```

This indicates all 3 nodes have joined the cluster.

### 3. Access the nodes

Each node is accessible on different ports:

| Service | Node 1 | Node 2 | Node 3 |
|---------|--------|--------|--------|
| Web UI | http://localhost:9091 | http://localhost:9092 | http://localhost:9093 |
| GraphQL | http://localhost:4001/graphql | http://localhost:4002/graphql | http://localhost:4003/graphql |
| OpenAPI | http://localhost:8081/api | http://localhost:8082/api | http://localhost:8083/api |

**Default Credentials**: `admin` / `admin`

## Testing Cache Eviction Propagation

### Scenario 1: Structure Modification

1. **Create a structure on Node 1**:
   ```bash
   curl -X POST http://localhost:8081/api/structures \
     -u admin:admin \
     -H "Content-Type: application/json" \
     -d '{
       "applicationId": "testApp",
       "name": "TestStructure",
       "properties": {...}
     }'
   ```

2. **Query the structure on all nodes** to populate caches:
   ```bash
   # Node 1
   curl http://localhost:8081/api/structures/testApp.TestStructure -u admin:admin
   
   # Node 2
   curl http://localhost:8082/api/structures/testApp.TestStructure -u admin:admin
   
   # Node 3
   curl http://localhost:8083/api/structures/testApp.TestStructure -u admin:admin
   ```

3. **Modify the structure on Node 1** (triggers cache eviction):
   ```bash
   curl -X PUT http://localhost:8081/api/structures/testApp.TestStructure \
     -u admin:admin \
     -H "Content-Type: application/json" \
     -d '{...modified structure...}'
   ```

4. **Check logs on all nodes** for cache eviction messages:
   ```bash
   # In separate terminals, watch logs for each node
   docker logs -f structures-node1 | grep "cache eviction"
   docker logs -f structures-node2 | grep "cache eviction"
   docker logs -f structures-node3 | grep "cache eviction"
   ```

   You should see messages like:
   ```
   Successfully completed cache eviction for structure: testApp.TestStructure due to Modify
   STRUCTURE cache eviction successfully completed on all 3 cluster nodes
   ```

5. **Verify metrics in Grafana**:
   - Open http://localhost:3000 (Grafana)
   - Navigate to the Structures Metrics dashboard
   - Check cache eviction metrics:
     - `cache_eviction_requests_total` - should increment by 1
     - `cache_eviction_cluster_results_total{result="success"}` - should increment
     - `cache_eviction_cluster_duration` - should show latency histogram

### Scenario 2: Structure Deletion

1. **Delete a structure on Node 1**:
   ```bash
   curl -X DELETE http://localhost:8081/api/structures/testApp.TestStructure \
     -u admin:admin
   ```

2. **Verify cache eviction** on all nodes (check logs as above)

3. **Verify structure is gone** on all nodes:
   ```bash
   # All should return 404
   curl http://localhost:8081/api/structures/testApp.TestStructure -u admin:admin
   curl http://localhost:8082/api/structures/testApp.TestStructure -u admin:admin
   curl http://localhost:8083/api/structures/testApp.TestStructure -u admin:admin
   ```

### Scenario 3: Named Query Modification

1. **Create a named query** on Node 1
2. **Execute the query** on all nodes (populates caches)
3. **Modify the named query** on Node 1 (triggers eviction)
4. **Verify eviction** propagated to all nodes via logs and metrics

## Testing Node Failure Scenarios

### Scenario 4: Node Failure During Eviction

1. **Ensure all 3 nodes are running and healthy**:
   ```bash
   curl http://localhost:9091/health/
   curl http://localhost:9092/health/
   curl http://localhost:9093/health/
   ```

2. **Stop Node 2**:
   ```bash
   docker stop structures-node2
   ```

3. **Trigger cache eviction** on Node 1 (modify a structure)

4. **Check logs** on Node 1:
   - Should see retry attempts
   - Should see "Waiting 1000ms before retry attempt"
   - Should eventually succeed with 2 nodes instead of 3

5. **Verify in logs**:
   ```
   Attempt 1: Broadcasting to 3 server nodes
   [ERROR] Cache eviction failed (Node 2 unreachable)
   Waiting 1000ms before retry attempt 2
   Attempt 2: Broadcasting to 2 server nodes (Node 2 excluded)
   SUCCESS: Cache eviction completed on 2 nodes
   ```

6. **Restart Node 2**:
   ```bash
   docker start structures-node2
   ```

7. **Verify it rejoins** the cluster (watch logs for topology update)

## Monitoring and Metrics

### View Cache Eviction Metrics

**Prometheus Queries**:

1. **Cache eviction request rate**:
   ```promql
   rate(cache_eviction_requests_total[5m])
   ```

2. **Success rate**:
   ```promql
   rate(cache_eviction_cluster_results_total{result="success"}[5m]) /
   rate(cache_eviction_cluster_results_total[5m])
   ```

3. **Average latency**:
   ```promql
   rate(cache_eviction_cluster_duration_sum[5m]) /
   rate(cache_eviction_cluster_duration_count[5m])
   ```

4. **Retry rate**:
   ```promql
   rate(cache_eviction_cluster_retries_total[5m])
   ```

5. **P95 latency**:
   ```promql
   histogram_quantile(0.95, rate(cache_eviction_cluster_duration_bucket[5m]))
   ```

Access metrics at:
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000

### View Traces

Access Jaeger at http://localhost:16686 to view distributed traces of cache eviction operations across the cluster.

### View Logs

Access Grafana at http://localhost:3000 and navigate to Explore → Loki to query logs from all nodes.

Example queries:
```
{service_name=~"structures-node."} |= "cache eviction"
```

## Troubleshooting

### Nodes not joining cluster

**Symptom**: Logs show "Waiting for topology snapshot" or timeout errors

**Solutions**:
1. Check Docker network: `docker network inspect docker-compose_default`
2. Verify discovery addresses: `CONTINUUM_CLUSTER_LOCAL_ADDRESSES` environment variable
3. Ensure discovery type is set: `CONTINUUM_CLUSTER_DISCOVERY_TYPE=SHAREDFS`
4. Check firewall rules for ports 47100 and 47500
5. Increase `CONTINUUM_CLUSTER_JOIN_TIMEOUT_MS` if nodes are slow to start

### Cache eviction not propagating

**Symptom**: Eviction succeeds on node 1 but not visible on other nodes

**Solutions**:
1. Check cluster topology: logs should show all nodes present
2. Verify network connectivity between containers
3. Check logs for "cache eviction failed" messages
4. Increase `STRUCTURES_CLUSTER_EVICTION_MAX_CACHE_SYNC_RETRY_ATTEMPTS` for more retries
5. Increase `STRUCTURES_CLUSTER_EVICTION_CACHE_SYNC_TIMEOUT_MS` for longer timeout per attempt
6. Verify Ignite cluster is actually formed (topology messages)

### High retry rate

**Symptom**: Metrics show many retries

**Solutions**:
1. Check network latency between nodes
2. Increase `STRUCTURES_CLUSTER_EVICTION_CACHE_SYNC_TIMEOUT_MS`
3. Check node health and resource usage
4. Look for node restarts or crashes in logs

### Memory issues

**Symptom**: Nodes crash or restart due to OOM

**Solutions**:
1. Reduce node count or increase Docker memory limit
2. Adjust JVM settings: `JAVA_TOOL_OPTIONS` and `BPL_JVM_HEAD_ROOM`
3. Reduce `CONTINUUM_MAX_OFF_HEAP_MEMORY`

**Additional Tuning Resources**:
- See `structures-core/IGNITE_KUBERNETES_TUNING.md` for comprehensive Ignite tuning guide
- Includes all Apache Ignite Kubernetes IP Finder configuration options
- Documents advanced tuning for geo-distributed clusters, fast failure detection
- RBAC requirements for Kubernetes deployments

## Cleanup

Stop and remove all containers:

```bash
docker compose -f compose.cluster-test.yml down
```

Remove volumes (deletes all data):

```bash
docker compose -f compose.cluster-test.yml down -v
```

## Performance Expectations

**Normal Operation**:
- Cache eviction should complete in < 1 second
- Retry rate should be < 1%
- Success rate should be > 99%
- P95 latency should be < 2 seconds

**During Node Failure**:
- First attempt will fail (to failed node)
- Retry should succeed within 1-2 seconds
- Total duration should be < 5 seconds

## Next Steps

Once manual testing is successful:
1. Run automated Testcontainers tests: `./gradlew :structures-core:test --tests ClusterCacheEvictionTest`
2. Run Kubernetes integration tests: `./gradlew :structures-core:test --tests K8sClusterCacheEvictionTest`
3. Set up alerts in production for high failure/retry rates
4. Document cluster size recommendations based on load testing
5. Configure Kubernetes discovery for production deployment

---

# Kubernetes Cluster Cache Eviction Testing

This section covers testing cache eviction in **Kubernetes** using the KinD (Kubernetes in Docker) cluster tooling.

## Overview

Kubernetes tests differ from Docker Compose tests:

| Aspect | Docker Compose | Kubernetes |
|--------|---------------|------------|
| Discovery | SHAREDFS | Kubernetes IP Finder |
| Access | Direct port mapping | kubectl port-forward |
| Test Type | Testcontainers (JVM in cluster) | JUnit (external, via REST API) |
| Pod Management | Docker Compose | StatefulSet |

## Prerequisites

1. **Docker** installed and running
2. **kubectl** installed and configured
3. **kind** CLI installed
4. **Helm** installed
5. At least **8GB RAM** available for Docker

## Setup Kubernetes Cluster

### 1. Create KinD Cluster

```bash
cd dev-tools/kind
./kind-cluster.sh create
```

This creates a KinD cluster with appropriate configuration for structures-server.

### 2. Build and Load Image

```bash
# Build structures-server image
./gradlew :structures-server:bootBuildImage

# Load image into KinD cluster
./dev-tools/kind/kind-cluster.sh load
```

### 3. Deploy structures-server

```bash
# Deploy with 3 replicas (required for cache eviction testing)
./dev-tools/kind/kind-cluster.sh deploy

# Wait for pods to be ready
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=structures --timeout=300s
```

### 4. Verify Cluster Formation

Check that all 3 pods have joined the Ignite cluster:

```bash
# Check pod logs for topology messages
kubectl logs -l app.kubernetes.io/name=structures --tail=50 | grep "Topology snapshot"
```

You should see messages like:
```
Topology snapshot [ver=3, servers=3, clients=0, ...]
```

## Running Automated K8s Tests

### Enable K8s Tests

Edit `structures-core/src/test/resources/application-k8s-test.yml`:

```yaml
structures:
  test:
    kubernetes:
      enabled: true  # Change from false to true
      namespace: default
      replicaCount: 3
```

### Run Tests

```bash
./gradlew :structures-core:test --tests K8sClusterCacheEvictionTest
```

The test will:
1. Check if K8s cluster is accessible via `kubectl cluster-info`
2. Discover structures-server pods using label selector
3. Start `kubectl port-forward` for each pod (ports 8081, 8082, 8083)
4. Create a Structure on pod 0
5. Warm caches by performing search on all pods
6. Modify Structure on pod 0 (triggers cache eviction)
7. Verify cache evicted on all pods within 5 seconds

### Test Output

**Success**:
```
=== Starting K8s cache eviction propagation test ===
Verifying all 3 pods are healthy
Creating test structure on pod 0
Warming caches on all 3 pods
Modifying structure on pod 0 to trigger cache eviction
Waiting for cache eviction to propagate across cluster (5 seconds)
Verifying cache eviction propagated to all 3 pods
=== Cache eviction propagation test PASSED ===
```

**Cluster Not Available** (tests skipped):
```
Kubernetes cluster not accessible and skipIfClusterNotRunning=true - skipping tests
```

## Manual Testing via kubectl port-forward

If you prefer manual testing over automated tests:

### 1. Start Port Forwarding

```bash
# Terminal 1 - Pod 0
kubectl port-forward pod/structures-0 8081:8080

# Terminal 2 - Pod 1
kubectl port-forward pod/structures-1 8082:8080

# Terminal 3 - Pod 2
kubectl port-forward pod/structures-2 8083:8080
```

### 2. Create Structure on Pod 0

```bash
curl -X POST http://localhost:8081/api/structures \
  -u admin:structures \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": "k8stest",
    "name": "CacheEvictionTest",
    "description": "Initial description",
    "properties": {}
  }'
```

### 3. Warm Caches on All Pods

```bash
# Pod 0
curl -X POST http://localhost:8081/api/k8stest/CacheEvictionTest/search \
  -u admin:structures \
  -H "Content-Type: text/plain" \
  -d '*'

# Pod 1
curl -X POST http://localhost:8082/api/k8stest/CacheEvictionTest/search \
  -u admin:structures \
  -H "Content-Type: text/plain" \
  -d '*'

# Pod 2
curl -X POST http://localhost:8083/api/k8stest/CacheEvictionTest/search \
  -u admin:structures \
  -H "Content-Type: text/plain" \
  -d '*'
```

### 4. Verify Initial State

```bash
# Check all pods see the initial description
curl http://localhost:8081/api/structures/k8stest.CacheEvictionTest -u admin:structures
curl http://localhost:8082/api/structures/k8stest.CacheEvictionTest -u admin:structures
curl http://localhost:8083/api/structures/k8stest.CacheEvictionTest -u admin:structures
```

### 5. Modify Structure on Pod 0

```bash
# First get the structure
STRUCTURE=$(curl -s http://localhost:8081/api/structures/k8stest.CacheEvictionTest -u admin:structures)

# Update the description
UPDATED=$(echo $STRUCTURE | jq '.description = "Updated description"')

# Put it back (triggers cache eviction)
curl -X PUT http://localhost:8081/api/structures/k8stest.CacheEvictionTest \
  -u admin:structures \
  -H "Content-Type: application/json" \
  -d "$UPDATED"
```

### 6. Verify Cache Eviction

Wait 5 seconds, then check all pods:

```bash
# All pods should now show the updated description
curl http://localhost:8081/api/structures/k8stest.CacheEvictionTest -u admin:structures | jq .description
curl http://localhost:8082/api/structures/k8stest.CacheEvictionTest -u admin:structures | jq .description
curl http://localhost:8083/api/structures/k8stest.CacheEvictionTest -u admin:structures | jq .description
```

### 7. Check Pod Logs

```bash
# View cache eviction logs on all pods
kubectl logs -l app.kubernetes.io/name=structures --tail=50 | grep "cache eviction"
```

You should see messages like:
```
Successfully completed cache eviction for structure: k8stest.CacheEvictionTest due to Modify
STRUCTURE cache eviction successfully completed on all 3 cluster nodes
```

## Troubleshooting K8s Tests

### Tests Skip Automatically

**Symptom**: Tests show "Kubernetes tests disabled - skipping"

**Solution**: Set `structures.test.kubernetes.enabled=true` in `application-k8s-test.yml`

### Cluster Not Accessible

**Symptom**: Tests show "Kubernetes cluster not accessible"

**Solutions**:
1. Verify cluster is running: `kubectl cluster-info`
2. Check current context: `kubectl config current-context`
3. Ensure KinD cluster is created: `kind get clusters`
4. Verify kubectl has proper access: `kubectl get nodes`

### Not Enough Pods

**Symptom**: "Expected 3 pod replicas but found only X"

**Solutions**:
1. Check pod status: `kubectl get pods -l app.kubernetes.io/name=structures`
2. Check deployment replicas: `kubectl get statefulset structures-server`
3. Increase replicas: `kubectl scale statefulset structures-server --replicas=3`
4. Check pod logs for startup errors: `kubectl logs structures-server-0`

### Port-Forward Fails

**Symptom**: "Health check did not become available"

**Solutions**:
1. Check pod is running: `kubectl get pod structures-server-0`
2. Check pod health: `kubectl exec structures-server-0 -- curl localhost:8080/health`
3. Check ports aren't already in use: `lsof -i :8081` (macOS/Linux)
4. Try different starting port in `application-k8s-test.yml`

### Cache Eviction Not Propagating

**Symptom**: Pods show different Structure descriptions after update

**Solutions**:
1. Verify all pods are in Ignite cluster:
   ```bash
   kubectl logs structures-server-0 | grep "Topology snapshot"
   ```
2. Check for cache eviction errors in logs:
   ```bash
   kubectl logs -l app.kubernetes.io/name=structures | grep -i "cache eviction failed"
   ```
3. Increase wait time in test (allow more time for propagation)
4. Check pod-to-pod network connectivity:
   ```bash
   kubectl exec structures-server-0 -- curl structures-server-1:8080/health
   ```

### RBAC Issues

**Symptom**: Pods can't discover each other via Kubernetes API

**Solutions**:
1. Verify ServiceAccount exists: `kubectl get serviceaccount structures`
2. Verify Role/RoleBinding: `kubectl get role,rolebinding -l app.kubernetes.io/name=structures`
3. Check Ignite logs for discovery errors:
   ```bash
   kubectl logs structures-server-0 | grep -i "kubernetes"
   ```
4. See `dev-tools/kind/KUBERNETES_RBAC.md` for RBAC requirements

## Cleanup

```bash
# Delete the KinD cluster
./dev-tools/kind/kind-cluster.sh delete

# Or just delete structures-server deployment
kubectl delete statefulset structures-server
kubectl delete service structures-server
```

## Differences from Docker Compose Testing

| Feature | Docker Compose | Kubernetes |
|---------|---------------|------------|
| **Test Location** | Test JVM joins cluster | Test JVM external to cluster |
| **Network Access** | Direct container ports | kubectl port-forward |
| **Discovery** | SHAREDFS | Kubernetes IP Finder |
| **Test Approach** | @SpringBootTest with clustering | REST API calls via HTTP |
| **Pod Lifecycle** | Can stop/start containers | Can delete/scale pods |
| **Cluster Formation** | Static IPs in compose file | Kubernetes Service discovery |

## Performance Expectations (K8s)

Same as Docker Compose:

**Normal Operation**:
- Cache eviction completes in < 1 second
- Success rate > 99%
- P95 latency < 2 seconds

**Propagation Window**:
- Tests allow 5 seconds for propagation
- Typically completes in 1-2 seconds
- Network latency may vary in K8s

## CI/CD Integration

### Skip K8s Tests by Default

Keep `enabled: false` in `application-k8s-test.yml` so tests skip by default on developer machines.

### Enable in CI Pipeline

```yaml
# GitHub Actions example
- name: Setup KinD Cluster
  run: |
    kind create cluster
    kubectl cluster-info

- name: Deploy structures-server
  run: |
    ./dev-tools/kind/kind-cluster.sh deploy

- name: Run K8s Tests
  run: |
    ./gradlew :structures-core:test --tests K8sClusterCacheEvictionTest
  env:
    STRUCTURES_TEST_KUBERNETES_ENABLED: true
```

## Summary

✅ **Docker Compose Tests** - Good for local development, side-by-side JVMs  
✅ **Kubernetes Tests** - Production-like environment, tests real K8s deployment  
✅ **Both Test Same Feature** - Cache eviction propagation via Ignite Compute Grid  
✅ **Different Approaches** - Testcontainers vs. kubectl port-forward

Choose the appropriate testing approach based on your needs:
- **Local Development**: Docker Compose (faster, simpler)
- **Pre-Production Validation**: Kubernetes (production-like)
- **CI/CD**: Both (comprehensive coverage)


