# Kubernetes Integration Tests

## Overview

These tests verify cache eviction propagation across a Kubernetes cluster by connecting to multiple pods via the Continuum WebSocket/STOMP protocol.

## Prerequisites

1. **Kubernetes cluster running** (KinD or any K8s cluster)
2. **structures-server deployed** with 3 replicas
3. **kubectl configured** and cluster accessible
4. **Node.js and npm** installed

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
```

Then run:
```bash
source .env.k8s && npm test -- k8s-cache-eviction.test.ts
```

## Test Architecture

### What It Tests

The test verifies that when a Structure is modified on one pod, the cache eviction event propagates to all other pods in the cluster via Apache Ignite Compute Grid.

### Test Flow

1. **Setup** - Discover all pods and create port-forwards
2. **Create** - Create a Structure on pod 0
3. **Warm caches** - Retrieve Structure from all pods (loads into cache)
4. **Modify** - Update Structure on pod 0 (triggers Ignite cache eviction broadcast)
5. **Wait** - Allow 5 seconds for cache eviction to propagate
6. **Verify** - Confirm all pods see the updated Structure

### Port Forwarding

The test automatically creates `kubectl port-forward` processes for each pod:
- Pod 0: `localhost:58511` → `pod:58503`
- Pod 1: `localhost:58512` → `pod:58503`
- Pod 2: `localhost:58513` → `pod:58503`

Each test connects/disconnects to pods as needed to verify behavior.

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
```

## Benefits

- ✅ **True integration testing** - Tests actual deployed cluster behavior
- ✅ **Uses real Continuum client** - Same client applications use
- ✅ **No mocking** - Tests real Ignite cache eviction
- ✅ **Verifies multi-pod behavior** - Ensures cluster-wide consistency
- ✅ **Easy to debug** - Can inspect pod logs independently
- ✅ **CI/CD ready** - Can run in automated pipelines

## Related Documentation

- [../../KUBERNETES_CACHE_EVICTION_TESTS.md](../../KUBERNETES_CACHE_EVICTION_TESTS.md) - Original requirements
- [../../docker-compose/CLUSTER_TESTING.md](../../docker-compose/CLUSTER_TESTING.md) - Docker Compose cluster testing
- [../../dev-tools/kind/README.md](../../dev-tools/kind/README.md) - KinD cluster setup
