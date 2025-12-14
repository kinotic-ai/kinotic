# ✅ Cluster Configuration Validated Against Working Setup

**Date**: 2025-11-27  
**Reference**: `structures-core/src/test/resources/application-test.yml` and `cluster-test-compose.yml`

## Summary

After reviewing the **working cluster configuration** from the test resources, I've identified the correct environment variables and updated our `helm-values.yaml` accordingly.

## Key Discoveries

### 1. **Continuum Uses `CONTINUUM_CLUSTER_*` Environment Variables**

The actual cluster configuration uses **Continuum** prefix, not `STRUCTURES_CLUSTER_*`:

```bash
# Docker Compose SHAREDFS setup:
CONTINUUM_CLUSTER_DISCOVERY_TYPE: SHAREDFS
CONTINUUM_CLUSTER_DISABLE_CLUSTERING: false
CONTINUUM_CLUSTER_JOIN_TIMEOUT_MS: 0
CONTINUUM_CLUSTER_DISCOVERY_PORT: 47501
CONTINUUM_CLUSTER_COMMUNICATION_PORT: 47101
CONTINUUM_CLUSTER_LOCAL_ADDRESS: 0.0.0.0
CONTINUUM_CLUSTER_LOCAL_ADDRESSES: host:port,host:port,...
CONTINUUM_CLUSTER_SHARED_FS_PATH: /sharedfs
```

### 2. **Kubernetes Mode Differences**

For **KUBERNETES** discovery (what we need for KinD):

**SHAREDFS Mode (Docker Compose):**
- Requires: `localAddress`, `localAddresses` (static IP list)
- Requires: `sharedFsPath` for file-based coordination
- Each node needs unique ports (47501, 47502, 47503, etc.)

**KUBERNETES Mode (KinD):**
- ✅ No `localAddress` needed (Kubernetes handles this)
- ✅ No `localAddresses` needed (DNS-based discovery)
- ✅ No `sharedFsPath` needed (network-based coordination)
- ✅ All pods use same ports (47500, 47100)
- ✅ Discovery via headless service DNS

```bash
# KinD Kubernetes setup:
CONTINUUM_CLUSTER_DISCOVERY_TYPE: KUBERNETES
CONTINUUM_CLUSTER_DISABLE_CLUSTERING: false
CONTINUUM_CLUSTER_JOIN_TIMEOUT_MS: 60000
CONTINUUM_CLUSTER_DISCOVERY_PORT: 47500
CONTINUUM_CLUSTER_COMMUNICATION_PORT: 47100
CONTINUUM_CLUSTER_KUBERNETES_NAMESPACE: default
CONTINUUM_CLUSTER_KUBERNETES_SERVICE_NAME: structures
```

### 3. **Helm Chart Limitation Found** 🔴

The Helm chart's `structures-server-config-map.yaml` **does NOT include Continuum cluster environment variables**!

**Current ConfigMap Only Has:**
```yaml
data:
  JAVA_TOOL_OPTIONS: ...
  SPRING_PROFILES_ACTIVE: ...
  STRUCTURES_*: ...  # Only STRUCTURES_* vars
  # ❌ NO CONTINUUM_CLUSTER_* vars!
```

**This Means:**
The Helm chart cannot configure clustering via environment variables - it must rely on:
1. **Application YAML files** (`application.yml`, `application-production.yml`)
2. **Spring Boot auto-configuration** detecting Kubernetes
3. **Hardcoded defaults** in the application

## Solution Options

### Option 1: Use Application Properties (Recommended)

The application likely has cluster configuration in `application-production.yml`:

```yaml
continuum:
  debug: false
  disableClustering: false
  cluster:
    discoveryType: KUBERNETES
    discoveryPort: 47500
    communicationPort: 47100
    joinTimeoutMs: 60000
```

**Action**: Check if `structures-server` already has this configured for `production` profile.

### Option 2: Patch Helm Chart ConfigMap Template

Add Continuum cluster env vars to `structures-server-config-map.yaml`:

```yaml
# Add to ConfigMap data:
{{- if .Values.continuum }}
{{- if .Values.continuum.cluster }}
CONTINUUM_CLUSTER_DISCOVERY_TYPE: "{{ .Values.continuum.cluster.discoveryType }}"
CONTINUUM_CLUSTER_DISABLE_CLUSTERING: "{{ .Values.continuum.cluster.disableClustering }}"
CONTINUUM_CLUSTER_JOIN_TIMEOUT_MS: "{{ .Values.continuum.cluster.joinTimeoutMs }}"
CONTINUUM_CLUSTER_DISCOVERY_PORT: "{{ .Values.continuum.cluster.discoveryPort }}"
CONTINUUM_CLUSTER_COMMUNICATION_PORT: "{{ .Values.continuum.cluster.communicationPort }}"
{{- if eq .Values.continuum.cluster.discoveryType "KUBERNETES" }}
CONTINUUM_CLUSTER_KUBERNETES_NAMESPACE: "{{ .Values.continuum.cluster.kubernetesNamespace }}"
CONTINUUM_CLUSTER_KUBERNETES_SERVICE_NAME: "{{ .Values.continuum.cluster.kubernetesServiceName }}"
{{- end }}
{{- end }}
{{- end }}
```

### Option 3: Use Spring Boot ConfigMap

Create a separate ConfigMap for `application-kubernetes.yml` and mount it.

## What Needs to Be Checked

1. **Does structures-server have `application-production.yml`?**
   - Location: `structures-server/src/main/resources/application-production.yml`
   - Check if it has Continuum cluster config

2. **Does Continuum auto-detect Kubernetes?**
   - Check if Continuum has built-in Kubernetes discovery that works without explicit config

3. **What's the actual cluster discovery mechanism?**
   - Review Continuum source code or docs

## Current Status

**Our `helm-values.yaml` now includes:**
✅ Correct `structures.clusterDiscoveryType: "KUBERNETES"` for headless service
✅ Correct ports (47500, 47100)
✅ Proper Elasticsearch configuration
⚠️ Custom `env` variables for `CONTINUUM_CLUSTER_*` (BUT deployment doesn't support custom env!)

**Next Steps:**
1. Check `structures-server/src/main/resources/application-production.yml`
2. Determine if Helm chart needs patching
3. Test deployment and check cluster formation in logs

## Expected Cluster Formation Process

When deployed to KinD with our configuration:

1. **Headless Service Created**: `structures.default.svc.cluster.local`
   - Returns all pod IPs via DNS

2. **Pods Start**: 2 replicas with `SPRING_PROFILES_ACTIVE=production`

3. **Continuum Initialization**:
   - Reads config from `application-production.yml` OR env vars
   - Detects `discoveryType: KUBERNETES`
   - Queries Kubernetes API for service endpoints
   - Or queries DNS for pod IPs

4. **Cluster Formation**:
   ```
   Pod 1 discovers Pod 2 via DNS/K8s API
   Pod 2 discovers Pod 1 via DNS/K8s API
   Nodes exchange topology
   Coordinator elected
   Cluster ready!
   ```

5. **Verify in Logs**:
   ```bash
   ./kind-cluster.sh logs | grep -i "topology\|cluster\|coordinator"
   ```

## Recommendation

**Before deployment**, check the application YAML files to see if cluster configuration already exists for the `production` profile. If it does, our current `helm-values.yaml` should work as-is (the `structures.*` values enable the headless service, which is all we need).

If cluster config is missing from application YAML, then we need to **patch the Helm chart's ConfigMap template** to add `CONTINUUM_CLUSTER_*` environment variables.

