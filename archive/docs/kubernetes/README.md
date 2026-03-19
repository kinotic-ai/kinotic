# Kubernetes Deployment Documentation

This section covers Kubernetes deployment, KinD (Kubernetes in Docker) local development, and related tooling.

## Documentation

### Quick Start

- [**KinD Quickstart**](./QUICKSTART_CLUSTER.md) - Get a local Kubernetes cluster running quickly
- [**KinD Tooling**](./KIND_TOOLING.md) - Scripts and utilities for KinD cluster management

### Configuration

- [**Cluster Configuration**](./CLUSTER_CONFIG_ANALYSIS.md) - Helm chart configuration for clustering
- [**RBAC Requirements**](./KUBERNETES_RBAC.md) - Service account permissions for Ignite discovery

### Testing

- [**Testing Setup**](./CLUSTER_TESTING_SETUP.md) - Overview of automated and manual testing infrastructure

## Quick Start with KinD

### 1. Create Cluster

```bash
cd dev-tools/kind
./kind-cluster.sh create
```

### 2. Build and Load Image

```bash
./gradlew :structures-server:bootBuildImage
./kind-cluster.sh load
```

### 3. Deploy

```bash
./kind-cluster.sh deploy
```

### 4. Verify

```bash
kubectl get pods -l app.kubernetes.io/name=structures
kubectl logs -l app.kubernetes.io/name=structures | grep "Topology snapshot"
```

## Helm Values for Kubernetes Clustering

```yaml
# helm-values.yaml
replicaCount: 3

kinotic:
  disableClustering: false
  cluster:
    discoveryType: KUBERNETES
    kubernetesNamespace: default
    kubernetesServiceName: structures
    discoveryPort: 47500
    communicationPort: 47100
    joinTimeoutMs: 60000
```

## See Also

- [Clustering Configuration](../clustering/IGNITE_CONFIGURATION_REFERENCE.md) - All cluster properties
- [Kubernetes Tuning](../clustering/IGNITE_KUBERNETES_TUNING.md) - Advanced K8s options
- [Cluster Testing](../clustering/CLUSTER_TESTING.md) - Testing procedures

---

**Last Updated**: January 2026

