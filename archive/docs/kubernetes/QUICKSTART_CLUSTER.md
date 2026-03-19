# 🚀 Quick Start: KinD Cluster with Coordination Testing

## TL;DR

```bash
# 1. Create KinD cluster
./dev-tools/kind/kind-cluster.sh create

# 2. Deploy structures-server with cluster coordination enabled
./dev-tools/kind/kind-cluster.sh deploy

# 3. Verify cluster formed (should show 2 servers)
./dev-tools/kind/kind-cluster.sh logs | grep "Topology snapshot"
# Expected: "Topology snapshot [ver=X, servers=2, ...]"

# 4. Access via port-forward (until service type fixed)
kubectl port-forward svc/structures-server 9090:9090

# 5. Open browser
open http://localhost:9090
```

## What Was Changed

### ✅ Helm Chart Patched
- **File**: `helm/structures/templates/structures-server-config-map.yaml`
- **Added**: `CONTINUUM_CLUSTER_*` environment variables
- **Effect**: ConfigMap now configures cluster coordination

### ✅ KinD Values Configured
- **File**: `dev-tools/kind/config/helm-values.yaml`
- **Added**: `kinotic.cluster.*` configuration
- **Effect**: Overrides `application-production.yml` (SHAREDFS → KUBERNETES)

## Verify It's Working

### Check ConfigMap:
```bash
kubectl get cm structures-server -o yaml | grep CONTINUUM
```

Should show:
```
CONTINUUM_CLUSTER_DISCOVERY_TYPE: KUBERNETES
CONTINUUM_CLUSTER_DISABLE_CLUSTERING: false
CONTINUUM_CLUSTER_DISCOVERY_PORT: 47500
CONTINUUM_CLUSTER_COMMUNICATION_PORT: 47100
...
```

### Check Headless Service:
```bash
kubectl get svc structures
```

Should show:
```
NAME         TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)
structures   ClusterIP   None         <none>        47500/TCP,47100/TCP
```

### Check Cluster Formation:
```bash
./dev-tools/kind/kind-cluster.sh logs --tail 1000 | grep -i "topology"
```

Should show:
```
Topology snapshot [ver=X, servers=2, clients=0, ...]
```

## Troubleshooting

### Pods Not Forming Cluster

**Check ConfigMap has variables:**
```bash
kubectl get cm structures-server -o yaml | grep CONTINUUM
```

**Check headless service exists:**
```bash
kubectl get svc structures
```

**Check DNS resolution:**
```bash
kubectl exec -it <pod-name> -- nslookup structures.default.svc.cluster.local
```

**Check pod logs for errors:**
```bash
kubectl logs <pod-name> | grep -i "error\|exception\|fail"
```

### Only 1 Server in Topology

**Check both pods are running:**
```bash
kubectl get pods
```

**Check for port conflicts:**
```bash
kubectl logs <pod-name> | grep -i "bind\|address.*use"
```

### Pods CrashLooping

**Check resource limits:**
```bash
kubectl describe pod <pod-name>
```

**Check logs:**
```bash
kubectl logs <pod-name> --previous
```

## Configuration Reference

### Cluster Modes:

| Mode | Use Case | Discovery |
|------|----------|-----------|
| LOCAL | Single node | In-memory only |
| SHAREDFS | Docker Compose | Shared filesystem |
| KUBERNETES | K8s/KinD | Headless service + DNS |

### Our Setup (KinD):

```yaml
kinotic:
  cluster:
    discoveryType: KUBERNETES  # ← K8s mode
    disableClustering: false   # ← Enable clustering
    discoveryPort: 47500       # ← Ignite discovery
    communicationPort: 47100   # ← Ignite comms
```

## Files Modified

1. `helm/structures/templates/structures-server-config-map.yaml` - Added Continuum vars
2. `dev-tools/kind/config/helm-values.yaml` - Configured for KUBERNETES mode

## Documentation

- Full details: `dev-tools/kind/HELM_CHART_PATCH.md`
- Analysis: `dev-tools/kind/CLUSTER_CONFIG_ANALYSIS.md`
- Validation: `dev-tools/kind/HELM_VALIDATION.md`
- README: `dev-tools/kind/README.md`

---

✅ **Ready for cluster coordination testing!**

