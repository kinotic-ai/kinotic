# ✅ Cluster Configuration - Implementation Complete

**Date**: January 2026  
**Reference**: `structures-core/src/test/resources/application-test.yml` and `helm/structures/templates/structures-server-config-map.yaml`

## Summary

The Helm chart now fully supports Continuum cluster configuration via environment variables. All `CONTINUUM_CLUSTER_*` variables are available and configurable through `helm-values.yaml`.

## Environment Variable Naming

The Helm ConfigMap uses underscore-separated names (which Spring Boot's relaxed binding accepts):

| Helm ConfigMap Variable | Application YAML Property | Helm Values Key |
|------------------------|---------------------------|-----------------|
| `CONTINUUM_CLUSTER_DISCOVERY_TYPE` | `continuum.cluster.discoveryType` | `continuum.cluster.discoveryType` |
| `CONTINUUM_CLUSTER_JOIN_TIMEOUT_MS` | `continuum.cluster.joinTimeoutMs` | `continuum.cluster.joinTimeoutMs` |
| `CONTINUUM_CLUSTER_DISCOVERY_PORT` | `continuum.cluster.discoveryPort` | `continuum.cluster.discoveryPort` |
| `CONTINUUM_CLUSTER_COMMUNICATION_PORT` | `continuum.cluster.communicationPort` | `continuum.cluster.communicationPort` |
| `CONTINUUM_CLUSTER_LOCAL_ADDRESS` | `continuum.cluster.localAddress` | `continuum.cluster.localAddress` |
| `CONTINUUM_CLUSTER_LOCAL_ADDRESSES` | `continuum.cluster.localAddresses` | `continuum.cluster.localAddresses` |
| `CONTINUUM_CLUSTER_KUBERNETES_NAMESPACE` | `continuum.cluster.kubernetesNamespace` | `continuum.cluster.kubernetesNamespace` |
| `CONTINUUM_CLUSTER_KUBERNETES_SERVICE_NAME` | `continuum.cluster.kubernetesServiceName` | `continuum.cluster.kubernetesServiceName` |
| `CONTINUUM_CLUSTER_SHARED_FS_PATH` | `continuum.cluster.sharedFsPath` | `continuum.cluster.sharedFsPath` |

## Helm Values Configuration

### KUBERNETES Discovery (Production/KinD)

```yaml
# helm-values.yaml
continuum:
  disableClustering: false
  cluster:
    discoveryType: KUBERNETES
    joinTimeoutMs: 60000
    discoveryPort: 47500
    communicationPort: 47100
    kubernetesNamespace: default
    kubernetesServiceName: structures
```

### SHAREDFS Discovery (Docker Compose)

```yaml
# helm-values.yaml
continuum:
  disableClustering: false
  cluster:
    discoveryType: SHAREDFS
    joinTimeoutMs: 30000
    discoveryPort: 47500
    communicationPort: 47100
    localAddress: "0.0.0.0"
    localAddresses: "node1:47500,node2:47500,node3:47500"
    sharedFsPath: /sharedfs
```

### LOCAL Discovery (Single Node/Development)

```yaml
# helm-values.yaml
continuum:
  disableClustering: false
  cluster:
    discoveryType: LOCAL
    discoveryPort: 47500
    communicationPort: 47100
```

## Discovery Type Comparison

| Feature | LOCAL | SHAREDFS | KUBERNETES |
|---------|-------|----------|------------|
| Use Case | Development | Docker Compose, VMs | Kubernetes, KinD |
| Node Discovery | None (single node) | Static IP list | K8s API / DNS |
| Required Config | None | `localAddresses`, `sharedFsPath` | `kubernetesNamespace`, `kubernetesServiceName` |
| Port Config | Same on all | Unique per node | Same on all pods |
| Headless Service | Not needed | Not needed | **Required** |

## Kubernetes-Specific Requirements

For `KUBERNETES` discovery to work:

1. **Headless Service**: Required for DNS-based pod discovery
   ```yaml
   apiVersion: v1
   kind: Service
   metadata:
     name: structures  # Must match kubernetesServiceName
   spec:
     clusterIP: None  # Headless
     selector:
       app.kubernetes.io/name: structures
     ports:
       - name: discovery
         port: 47500
       - name: communication
         port: 47100
   ```

2. **RBAC Permissions**: Service account needs `get`, `list` on `endpoints` and `pods`

3. **Same Namespace**: All pods must be in `kubernetesNamespace`

## Helm ConfigMap Template

The ConfigMap template (`helm/structures/templates/structures-server-config-map.yaml`) now includes:

```yaml
{{- if .Values.continuum }}
{{- if .Values.continuum.cluster }}
# Continuum Cluster Configuration
CONTINUUM_CLUSTER_DISABLE_CLUSTERING: "{{ .Values.continuum.disableClustering | default "false" }}"
CONTINUUM_CLUSTER_DISCOVERY_TYPE: "{{ .Values.continuum.cluster.discoveryType | default "LOCAL" | upper }}"
CONTINUUM_CLUSTER_JOIN_TIMEOUT_MS: "{{ .Values.continuum.cluster.joinTimeoutMs | default "0" }}"
CONTINUUM_CLUSTER_DISCOVERY_PORT: "{{ .Values.continuum.cluster.discoveryPort | default "47500" }}"
CONTINUUM_CLUSTER_COMMUNICATION_PORT: "{{ .Values.continuum.cluster.communicationPort | default "47100" }}"
CONTINUUM_CLUSTER_LOCAL_ADDRESS: "{{ .Values.continuum.cluster.localAddress | default "" }}"

{{- if eq (.Values.continuum.cluster.discoveryType | default "LOCAL" | upper) "KUBERNETES" }}
CONTINUUM_CLUSTER_KUBERNETES_NAMESPACE: "{{ .Values.continuum.cluster.kubernetesNamespace | default "default" }}"
CONTINUUM_CLUSTER_KUBERNETES_SERVICE_NAME: "{{ .Values.continuum.cluster.kubernetesServiceName | default "structures" }}"
{{- end }}

{{- if eq (.Values.continuum.cluster.discoveryType | default "LOCAL" | upper) "SHAREDFS" }}
CONTINUUM_CLUSTER_SHARED_FS_PATH: "{{ .Values.continuum.cluster.sharedFsPath | default "/sharedfs" }}"
{{- end }}

{{- if eq (.Values.continuum.cluster.discoveryType | default "LOCAL" | upper) "LOCAL" }}
CONTINUUM_CLUSTER_LOCAL_ADDRESSES: "{{ .Values.continuum.cluster.localAddresses | default "" }}"
{{- end }}
{{- end }}
{{- end }}
```

## Cluster Formation Process

When deployed to KinD/Kubernetes:

1. **Helm Install**: Creates ConfigMap with `CONTINUUM_CLUSTER_*` env vars
2. **Pods Start**: Environment variables configure Continuum cluster settings
3. **Ignite Discovery**: Uses `TcpDiscoveryKubernetesIpFinder` to find other pods
4. **Cluster Forms**: Nodes exchange topology, coordinator elected
5. **Ready**: Cluster operational

**Verify in Logs**:
```bash
kubectl logs -l app.kubernetes.io/name=structures | grep -i "topology\|cluster\|coordinator"
```

Expected output:
```
Topology snapshot [ver=3, servers=3, clients=0, ...]
```

## Status: ✅ COMPLETE

- ✅ Helm ConfigMap includes all `CONTINUUM_CLUSTER_*` variables
- ✅ Conditional rendering for each discovery type
- ✅ Sensible defaults for all properties
- ✅ Headless service template available
- ✅ RBAC template available

## See Also

- [IGNITE_CONFIGURATION_REFERENCE.md](../../structures-core/IGNITE_CONFIGURATION_REFERENCE.md) - All cluster properties
- [IGNITE_KUBERNETES_TUNING.md](../../structures-core/IGNITE_KUBERNETES_TUNING.md) - Advanced Kubernetes tuning
- [CLUSTER_TESTING.md](../../docker-compose/CLUSTER_TESTING.md) - Testing procedures
