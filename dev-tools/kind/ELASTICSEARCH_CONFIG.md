# Elasticsearch Configuration for KinD

## Official Elastic Image vs Bitnami

**Changed from**: Bitnami Elasticsearch Helm chart  
**Changed to**: Official Elastic Helm chart with official Elastic image

## Why the Change?

1. **Official Image**: `docker.elastic.co/elasticsearch/elasticsearch:8.18.1` is the official Elastic image
2. **Matches docker-compose**: Uses the same image as `compose.ek-stack.yml`
3. **Better Compatibility**: Official chart has better support for Elasticsearch features
4. **Version Accuracy**: Elastic versioning is more reliable

## Configuration

### Image
```yaml
image: docker.elastic.co/elasticsearch/elasticsearch:8.18.1
```

### Key Settings (matching docker-compose)
```yaml
xpack.security.enabled: false       # Disable security for local dev
discovery.type: single-node         # Single node mode (no cluster)
cluster.name: structures-cluster    # Cluster name
```

### Resources
```yaml
esJavaOpts: -Xms512m -Xmx512m      # Java heap size
resources:
  requests:
    cpu: 500m
    memory: 1Gi
  limits:
    memory: 1Gi
```

### Persistence
```yaml
persistence.enabled: false          # Use emptyDir for local dev (faster)
```

## Helm Chart Comparison

### Bitnami (OLD)
```bash
helm install elasticsearch bitnami/elasticsearch \
  --set master.replicaCount=1 \
  --set data.replicaCount=0
```

**Issues**:
- Different image source
- Complex master/data/coordinating node structure
- Not matching production Elastic images

### Official Elastic (NEW)
```bash
helm install elasticsearch elastic/elasticsearch \
  --set image="docker.elastic.co/elasticsearch/elasticsearch" \
  --set imageTag="8.18.1" \
  --set replicas=1
```

**Benefits**:
- ✅ Official Elastic image
- ✅ Simple single-node configuration
- ✅ Matches docker-compose exactly
- ✅ Better documentation and support

## Service Name

The official Elastic chart creates a service named:
```
elasticsearch-master
```

This matches our Helm values configuration:
```yaml
properties:
  structures:
    elastic:
      connections:
        - host: "elasticsearch-master"
          port: 9200
```

## Verification

After deployment:

```bash
# Check pod
kubectl get pods -l app=elasticsearch-master

# Check service
kubectl get svc elasticsearch-master

# Test connection
kubectl exec -it <structures-pod> -- curl http://elasticsearch-master:9200

# Check cluster health (should be yellow for single node)
kubectl exec -it <structures-pod> -- curl http://elasticsearch-master:9200/_cluster/health
```

Expected response:
```json
{
  "cluster_name": "structures-cluster",
  "status": "yellow",  // Yellow is OK for single node
  "number_of_nodes": 1,
  "number_of_data_nodes": 1
}
```

## Troubleshooting

### Pod Crashes with "max virtual memory areas vm.max_map_count too low"

This is a common Elasticsearch issue. Fix:

```bash
# On Linux host
sudo sysctl -w vm.max_map_count=262144

# Or in Docker Desktop settings (macOS/Windows)
# Resources → Advanced → vm.max_map_count: 262144
```

### Pod Shows "yellow" health status

**This is normal for single-node clusters!**
- Green = all replicas assigned (requires multiple nodes)
- Yellow = primary shards assigned, replicas not (single node)
- Red = primary shards not assigned (problem!)

### Connection Refused from structures-server

Check service and endpoints:
```bash
kubectl get svc elasticsearch-master
kubectl get endpoints elasticsearch-master
```

Should show pod IP in endpoints.

## Docker Compose Reference

This configuration matches:
```yaml
# docker-compose/compose.ek-stack.yml
services:
  structures-elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.18.1
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
    ports:
      - "9200:9200"
```

## Future Improvements

For production-like testing:
- Enable persistence: `--set persistence.enabled=true`
- Add more replicas: `--set replicas=3`
- Enable security: `--set xpack.security.enabled=true`
- Add monitoring: Deploy Kibana alongside

