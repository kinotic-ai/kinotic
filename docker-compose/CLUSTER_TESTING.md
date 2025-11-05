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
2. Verify discovery addresses: `STRUCTURES_CLUSTER_SHARED_FS_ADDRESSES` environment variable
3. Ensure discovery type is set: `STRUCTURES_CLUSTER_DISCOVERY_TYPE=sharedfs`
4. Check firewall rules for ports 47100 and 47500
5. Increase `STRUCTURES_CLUSTER_JOIN_TIMEOUT_MS` if nodes are slow to start

### Cache eviction not propagating

**Symptom**: Eviction succeeds on node 1 but not visible on other nodes

**Solutions**:
1. Check cluster topology: logs should show all nodes present
2. Verify network connectivity between containers
3. Check logs for "cache eviction failed" messages
4. Increase `STRUCTURES_MAX_CLUSTER_SYNC_RETRY_ATTEMPTS` for more retries
5. Increase `STRUCTURES_CLUSTER_SYNC_TIMEOUT_MS` for longer timeout per attempt
6. Verify Ignite cluster is actually formed (topology messages)

### High retry rate

**Symptom**: Metrics show many retries

**Solutions**:
1. Check network latency between nodes
2. Increase `STRUCTURES_CLUSTER_SYNC_TIMEOUT_MS`
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
2. Set up alerts in production for high failure/retry rates
3. Document cluster size recommendations based on load testing
4. Configure Kubernetes discovery for production deployment


