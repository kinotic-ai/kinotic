# Helm Configuration Validation Report

**Date**: 2025-11-27  
**Feature**: KinD Cluster Developer Tools  
**Validation**: helm-values.yaml compatibility with helm/structures chart

## Summary

✅ **Configuration is now COMPATIBLE** with the Helm chart after corrections.

## Key Findings

### 1. Value Structure Requirements ✅ FIXED

The Helm chart expects values in a specific structure:

- `replicaCount` - Number of pod replicas
- `image.*` - Container image configuration
- `properties.structures.*` - Application configuration (set as ConfigMap env vars)
- `structures.*` - Top-level config for Kubernetes service template

### 2. Cluster Configuration Requirements ✅ CRITICAL FOR TESTING

For **multi-pod cluster coordination** (your primary use case), the Helm chart:

**Creates a Headless Service** when:
```yaml
structures:
  clusterDiscoveryType: "KUBERNETES"  # MUST be uppercase!
```

This headless service is **essential** for Ignite node discovery in Kubernetes.

**Service Details:**
- Name: `structures` (configurable via `clusterKubernetesServiceName`)
- Type: Headless (`clusterIP: None`)
- Ports:
  - Discovery: 47500
  - Communication: 47100
- `publishNotReadyAddresses: true` - Allows DNS lookup before pods ready

### 3. Configuration Mapping

The chart uses **environment variables** (not direct Ignite config):

```yaml
properties:
  structures:
    elastic:
      connections:
        - host: "elasticsearch-master"
          port: 9200
          scheme: "http"
```

These become ConfigMap entries like:
```
STRUCTURES_ELASTICCONNECTIONS_0_HOST=elasticsearch-master
STRUCTURES_ELASTICCONNECTIONS_0_PORT=9200
STRUCTURES_ELASTICCONNECTIONS_0_SCHEME=http
```

The application then reads these and configures itself accordingly.

## Corrected Configuration

The updated `helm-values.yaml` now includes:

### Essential Changes:

1. **Proper Structure**: Values match chart's expected paths
   ```yaml
   properties:
     structures:
       elastic: ...  # Not top-level elasticsearch:
   ```

2. **Cluster Discovery Enabled**:
   ```yaml
   structures:
     clusterDiscoveryType: "KUBERNETES"  # Uppercase for template match
   ```

3. **Elasticsearch Connection**: Uses Bitnami service name
   ```yaml
   connections:
     - host: "elasticsearch-master"  # From Bitnami chart
       port: 9200
       scheme: "http"
   ```

4. **Image Pull Policy**: Set to `Never` for local images
   ```yaml
   image:
     pullPolicy: Never  # Don't try to pull from registry
   ```

## What Happens at Deployment

When you run `./kind-cluster.sh deploy`:

1. **Headless Service Created**: `structures` service with no ClusterIP
   - Allows DNS-based discovery: `structures.default.svc.cluster.local`
   - Each pod gets: `structures-server-0.structures.default.svc.cluster.local`

2. **Pods Created**: 2 replicas as configured
   - Pod 0: `structures-server-xxxxx-aaaa`
   - Pod 1: `structures-server-xxxxx-bbbb`

3. **Ignite Initialization**: Each pod:
   - Queries DNS for `structures.default.svc.cluster.local`
   - Gets IP addresses of all pods
   - Attempts connection on port 47500 (discovery)
   - Forms cluster when nodes find each other

4. **Cluster Formation**: Within ~60 seconds (clusterJoinTimeoutMs)
   - Nodes exchange topology information
   - Elect coordinator
   - Synchronize data partitions
   - Become ready for requests

## Testing Cluster Coordination

After deployment, you can verify cluster formation:

```bash
# Check pod logs for cluster messages
./kind-cluster.sh logs --tail 200 | grep -i "topology"

# Expected output includes:
# - "Topology snapshot"
# - "Nodes: 2"
# - "Servers: 2"
# - Coordinator election messages
```

## Remaining Questions

### OIDC Configuration

The original `helm-values.yaml` included OIDC settings, but the Helm chart **does not have OIDC configuration**. 

**Questions:**
1. Is OIDC configured via Spring Boot properties elsewhere?
2. Should OIDC be added to the ConfigMap template?
3. Is there a separate auth configuration mechanism?

**Recommendation**: Check if structures-auth module handles OIDC via auto-configuration or if it needs to be added to the Helm chart's ConfigMap template.

### Service Exposure

The chart defines a Service at `structures-server-service.yaml`:

**Current Helm Chart:**
```yaml
spec:
  type: LoadBalancer  # ⚠️ Problem for KinD!
  ports:
    - name: "ui"
      port: 9090
      targetPort: 9090
    # ... more ports
```

**Problem**: `LoadBalancer` doesn't work in KinD without additional setup (MetalLB).

**Solution**: Override in helm-values.yaml (but chart doesn't support this - **CHART BUG**).

The chart should allow:
```yaml
service:
  type: NodePort
  nodePort: 30090
```

But it doesn't have this in the template! We need to either:
1. **Patch the Helm chart** to make service type configurable
2. **Use port-forward** for access instead of NodePort

## Final Recommendations

### ✅ Configuration is Now Valid for Cluster Testing

The corrected `helm-values.yaml` will:
- ✅ Create 2 replicas
- ✅ Enable Kubernetes discovery
- ✅ Form an Ignite cluster
- ✅ Connect to Elasticsearch
- ⚠️ Create LoadBalancer service (won't have external IP in KinD)

### 🔧 Required Chart Fixes

To fully support KinD/local development, the Helm chart needs updates:

1. **Make Service Type Configurable**:
   ```yaml
   # In structures-server-service.yaml
   type: {{ .Values.service.type | default "LoadBalancer" }}
   {{- if eq .Values.service.type "NodePort" }}
   nodePort: {{ .Values.service.nodePort }}
   {{- end }}
   ```

2. **Add OIDC Configuration** (if needed):
   The chart has no OIDC environment variables. Need to determine if:
   - OIDC is configured via structures-auth auto-configuration
   - Or needs to be added to ConfigMap template

### 🚀 Workaround for Now

Until chart is fixed, use `kubectl port-forward`:

```bash
# After deployment
kubectl port-forward svc/structures-server 9090:9090

# Then access at http://localhost:9090
```

Or update the chart template directly in your fork.

## Summary

✅ **Cluster coordination will work!**  
⚠️ **Access requires port-forward instead of NodePort**  
❓ **OIDC configuration needs investigation**

The key fix was setting `structures.clusterDiscoveryType: "KUBERNETES"` to enable the headless service for node discovery.

