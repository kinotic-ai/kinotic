# ✅ Helm Chart Patched for Cluster Coordination

**Date**: 2025-11-27  
**Changes**: Added Continuum cluster configuration to Helm chart

## Summary of Changes

### 1. **Helm Chart ConfigMap Template** - PATCHED ✅

**File**: `/Users/nic/git/structures/helm/structures/templates/structures-server-config-map.yaml`

**Added Section** (lines 33-50):
```yaml
{{- if .Values.continuum }}
{{- if .Values.continuum.cluster }}
# Continuum Cluster Configuration
CONTINUUM_CLUSTER_DISCOVERY_TYPE: "{{ .Values.continuum.cluster.discoveryType | default "LOCAL" | upper }}"
CONTINUUM_CLUSTER_DISABLE_CLUSTERING: "{{ .Values.continuum.cluster.disableClustering | default "false" }}"
CONTINUUM_CLUSTER_JOIN_TIMEOUT_MS: "{{ .Values.continuum.cluster.joinTimeoutMs | default "60000" }}"
CONTINUUM_CLUSTER_DISCOVERY_PORT: "{{ .Values.continuum.cluster.discoveryPort | default "47500" }}"
CONTINUUM_CLUSTER_COMMUNICATION_PORT: "{{ .Values.continuum.cluster.communicationPort | default "47100" }}"
{{- if eq (.Values.continuum.cluster.discoveryType | default "LOCAL" | upper) "KUBERNETES" }}
# Kubernetes-specific cluster discovery settings
CONTINUUM_CLUSTER_KUBERNETES_NAMESPACE: "{{ .Values.continuum.cluster.kubernetesNamespace | default "default" }}"
CONTINUUM_CLUSTER_KUBERNETES_SERVICE_NAME: "{{ .Values.continuum.cluster.kubernetesServiceName | default "structures" }}"
{{- end }}
{{- if .Values.continuum.cluster.maxOffHeapMemory }}
CONTINUUM_MAX_OFF_HEAP_MEMORY: "{{ .Values.continuum.cluster.maxOffHeapMemory }}"
{{- end }}
{{- end }}
{{- end }}
```

**What This Does**:
- Creates `CONTINUUM_CLUSTER_*` environment variables in the ConfigMap
- Uses values from `helm-values.yaml` under `continuum.cluster.*`
- Conditionally adds Kubernetes-specific vars when `discoveryType: KUBERNETES`
- Provides sensible defaults for all values

### 2. **KinD Helm Values** - UPDATED ✅

**File**: `/Users/nic/git/structures/dev-tools/kind/config/helm-values.yaml`

**Added Section**:
```yaml
continuum:
  cluster:
    discoveryType: "KUBERNETES"      # Overrides application-production.yml (SHAREDFS)
    disableClustering: false         # Enable multi-node clustering
    joinTimeoutMs: 60000             # 60 seconds to form cluster
    discoveryPort: 47500             # Ignite discovery port
    communicationPort: 47100         # Ignite communication port
    kubernetesNamespace: "default"   # K8s namespace for service discovery
    kubernetesServiceName: "structures"  # Headless service name
    maxOffHeapMemory: 419430400      # ~400MB off-heap (matches docker-compose)
```

**What This Does**:
- Configures Continuum to use **KUBERNETES discovery** (not SHAREDFS from application-production.yml)
- Enables clustering for the 2 replica pods
- Sets proper ports and timeouts matching the working docker-compose config
- Provides Kubernetes service details for node discovery

## How It Works

### Deployment Flow:

1. **Helm Install/Upgrade**:
   ```bash
   ./kind-cluster.sh deploy
   ```

2. **ConfigMap Created** with environment variables:
   ```yaml
   CONTINUUM_CLUSTER_DISCOVERY_TYPE: KUBERNETES
   CONTINUUM_CLUSTER_DISABLE_CLUSTERING: false
   CONTINUUM_CLUSTER_JOIN_TIMEOUT_MS: 60000
   CONTINUUM_CLUSTER_DISCOVERY_PORT: 47500
   CONTINUUM_CLUSTER_COMMUNICATION_PORT: 47100
   CONTINUUM_CLUSTER_KUBERNETES_NAMESPACE: default
   CONTINUUM_CLUSTER_KUBERNETES_SERVICE_NAME: structures
   CONTINUUM_MAX_OFF_HEAP_MEMORY: 419430400
   ```

3. **Headless Service Created** (from `ignite-service.yaml`):
   ```yaml
   apiVersion: v1
   kind: Service
   metadata:
     name: structures
   spec:
     clusterIP: None  # Headless!
     ports:
       - name: discovery
         port: 47500
       - name: communication
         port: 47100
   ```

4. **Pods Start** (2 replicas):
   - Read environment variables from ConfigMap
   - Override `application-production.yml` settings
   - Discover `discoveryType: KUBERNETES`

5. **Cluster Formation**:
   - Each pod queries Kubernetes API for service endpoints
   - Gets IP addresses of all pods in `structures` service
   - Connects on port 47500 (discovery)
   - Forms Ignite cluster
   - Elects coordinator
   - Synchronizes data

### Expected Log Output:

```bash
./kind-cluster.sh logs | grep -i "topology\|cluster\|coordinator"
```

You should see:
```
[INFO] Topology snapshot [ver=1, servers=2, clients=0, state=ACTIVE, CPUs=4, offheap=0.4GB, heap=2.0GB]
[INFO] Coordinator: structures-server-xxxxx-aaaa
[INFO] Local node: structures-server-xxxxx-bbbb
[INFO] Cluster activated
```

## Validation

To verify the configuration is correct:

### 1. Check ConfigMap After Deployment:
```bash
kubectl get configmap structures-server -o yaml | grep CONTINUUM
```

Expected output:
```yaml
CONTINUUM_CLUSTER_COMMUNICATION_PORT: "47100"
CONTINUUM_CLUSTER_DISABLE_CLUSTERING: "false"
CONTINUUM_CLUSTER_DISCOVERY_PORT: "47500"
CONTINUUM_CLUSTER_DISCOVERY_TYPE: "KUBERNETES"
CONTINUUM_CLUSTER_JOIN_TIMEOUT_MS: "60000"
CONTINUUM_CLUSTER_KUBERNETES_NAMESPACE: "default"
CONTINUUM_CLUSTER_KUBERNETES_SERVICE_NAME: "structures"
CONTINUUM_MAX_OFF_HEAP_MEMORY: "419430400"
```

### 2. Check Headless Service:
```bash
kubectl get svc structures -o yaml
```

Expected:
```yaml
spec:
  clusterIP: None  # Headless!
  ports:
  - name: discovery
    port: 47500
  - name: communication
    port: 47100
```

### 3. Check DNS Resolution:
```bash
kubectl exec -it <pod-name> -- nslookup structures.default.svc.cluster.local
```

Should return multiple IP addresses (one per pod).

### 4. Verify Cluster Formation:
```bash
./kind-cluster.sh logs --tail 500 | grep -E "Topology|Coordinator|Local node"
```

## Benefits of This Approach

✅ **Centralized Configuration**: All cluster settings in `helm-values.yaml`  
✅ **Environment Overrides**: Can use different values for dev/staging/prod  
✅ **Backward Compatible**: Still works with original `values.yaml` (defaults to LOCAL)  
✅ **Type-Safe**: Helm validates the YAML structure  
✅ **Documented**: Clear comments explain each setting  
✅ **Tested Pattern**: Matches working docker-compose cluster setup

## Testing the Changes

```bash
# 1. Deploy to KinD
./kind-cluster.sh deploy

# 2. Check ConfigMap has Continuum vars
kubectl get configmap structures-server -o yaml | grep CONTINUUM

# 3. Check pods are running
kubectl get pods

# 4. Check cluster formation in logs
./kind-cluster.sh logs --tail 500 | grep -i "topology"

# Expected: "Topology snapshot [ver=X, servers=2, ...]"

# 5. Check coordinator election
./kind-cluster.sh logs | grep -i "coordinator"

# Expected: "Coordinator: structures-server-xxxxx-aaaa"
```

## Next Steps

1. ✅ Deploy to KinD and verify cluster formation
2. Run integration tests against the cluster
3. If successful, consider committing these changes to the main Helm chart
4. Document this pattern for other environments (staging, production)

## Related Files

- **Helm Chart**: `/Users/nic/git/structures/helm/structures/`
- **KinD Config**: `/Users/nic/git/structures/dev-tools/kind/config/helm-values.yaml`
- **Reference**: `/Users/nic/git/structures/structures-core/src/test/resources/docker-compose/cluster-test-compose.yml`
- **Analysis**: `/Users/nic/git/structures/dev-tools/kind/CLUSTER_CONFIG_ANALYSIS.md`
- **Validation**: `/Users/nic/git/structures/dev-tools/kind/HELM_VALIDATION.md`

---

🎉 **The Helm chart is now ready for Kubernetes cluster coordination testing!**

