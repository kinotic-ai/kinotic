# Clustering and High Availability Documentation

This index provides an overview of all clustering and high availability documentation for the Structures project.

---

## Quick Start

| Your Goal | Start Here |
|-----------|------------|
| **Configure clustering** | [IGNITE_CONFIGURATION_REFERENCE.md](./IGNITE_CONFIGURATION_REFERENCE.md) |
| **Deploy to Kubernetes** | [IGNITE_KUBERNETES_TUNING.md](./IGNITE_KUBERNETES_TUNING.md) |
| **Test cluster setup** | [CLUSTER_TESTING.md](./CLUSTER_TESTING.md) |
| **Understand cache eviction** | [CACHE_EVICTION_DESIGN.md](./CACHE_EVICTION_DESIGN.md) |
| **Use KinD for local K8s** | [QUICKSTART_CLUSTER.md](../kubernetes/QUICKSTART_CLUSTER.md) |

---

## Documentation Guide

### Configuration Documentation

| Document | Purpose | Audience |
|----------|---------|----------|
| [IGNITE_CONFIGURATION_REFERENCE.md](./IGNITE_CONFIGURATION_REFERENCE.md) | Quick reference for all cluster configuration properties | Operators, DevOps |
| [IGNITE_KUBERNETES_TUNING.md](./IGNITE_KUBERNETES_TUNING.md) | Comprehensive Kubernetes deployment and tuning guide (30+ options) | Operators, Platform Engineers |

### Architecture and Design

| Document | Purpose | Audience |
|----------|---------|----------|
| [CACHE_EVICTION_DESIGN.md](./CACHE_EVICTION_DESIGN.md) | Cache eviction architecture, cluster-wide eviction, OpenTelemetry metrics | Developers, Architects |

### Testing Documentation

| Document | Purpose | Audience |
|----------|---------|----------|
| [CLUSTER_TESTING.md](./CLUSTER_TESTING.md) | Manual testing guide for Docker Compose and Kubernetes | QA, DevOps |
| [CLUSTER_TESTING_SETUP.md](../kubernetes/CLUSTER_TESTING_SETUP.md) | Implementation summary and quick start | Developers |

### KinD (Kubernetes in Docker) Documentation

| Document | Purpose | Audience |
|----------|---------|----------|
| [QUICKSTART_CLUSTER.md](../kubernetes/QUICKSTART_CLUSTER.md) | Quick start for KinD cluster with coordination testing | Developers |
| [KIND_TOOLING.md](../kubernetes/KIND_TOOLING.md) | KinD tooling overview | Developers |
| [KUBERNETES_RBAC.md](../kubernetes/KUBERNETES_RBAC.md) | RBAC requirements for Kubernetes deployment | Operators |

---

## Cluster Discovery Types

Structures supports three cluster discovery mechanisms via `continuum.cluster.discoveryType`:

| Type | Use Case | Documentation |
|------|----------|---------------|
| `LOCAL` | Single-node development | [IGNITE_CONFIGURATION_REFERENCE.md](./IGNITE_CONFIGURATION_REFERENCE.md) |
| `SHAREDFS` | Docker Compose, VMs | [CLUSTER_TESTING.md](./CLUSTER_TESTING.md) |
| `KUBERNETES` | Production Kubernetes | [IGNITE_KUBERNETES_TUNING.md](./IGNITE_KUBERNETES_TUNING.md) |

---

## Common Tasks

### Setting Up a Development Cluster

```bash
# Option 1: Docker Compose (3-node cluster)
cd docker-compose
docker compose -f compose.cluster-test.yml up

# Option 2: KinD (Kubernetes in Docker)
./dev-tools/kind/kind-cluster.sh create
./dev-tools/kind/kind-cluster.sh deploy
```

### Running Cluster Tests

```bash
# Build server image first
./gradlew :structures-server:bootBuildImage

# Run automated cluster tests
./gradlew :structures-core:clusterTest

# Run manual tests
# See CLUSTER_TESTING.md
```

### Deploying to Production Kubernetes

```bash
helm install structures ./helm/structures \
  --set replicaCount=3 \
  --set continuum.cluster.discoveryType=KUBERNETES \
  --set continuum.cluster.kubernetesNamespace=production \
  --set continuum.cluster.kubernetesServiceName=structures-ignite
```

---

## Monitoring and Metrics

Cache eviction operations expose OpenTelemetry metrics:

| Metric | Type | Description |
|--------|------|-------------|
| `cache.eviction.requests` | Counter | Total cache eviction requests |
| `cache.eviction.cluster.results` | Counter | Cluster eviction success/failure |
| `cache.eviction.cluster.duration` | Histogram | Cluster eviction latency |
| `cache.eviction.cluster.retries` | Counter | Retry attempts |

See [CACHE_EVICTION_DESIGN.md](./CACHE_EVICTION_DESIGN.md) for Prometheus queries and alerting recommendations.

---

## Related Documentation

- **OIDC Authentication**: [../oidc/](../oidc/)
- **Kubernetes Deployment**: [../kubernetes/](../kubernetes/)

---

**Last Updated**: January 2026
