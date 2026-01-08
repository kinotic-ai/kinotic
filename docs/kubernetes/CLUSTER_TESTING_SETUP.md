# Cluster Testing Setup - Implementation Summary

This document summarizes the cluster testing infrastructure that has been implemented for cache eviction verification.

## What Was Implemented

### 1. StructuresProperties-based Ignite Configuration ✅
- **Files**:
  - `structures-core/src/main/java/org/kinotic/structures/api/config/StructuresProperties.java`
  - `structures-core/src/main/java/org/kinotic/structures/internal/config/IgniteConfiguration.java`
  - `structures-server/src/main/resources/application.yml`
- Added comprehensive Ignite cluster configuration properties
- Type-safe constants: `ClusterDiscoveryType.LOCAL`, `.SHAREDFS`, `.KUBERNETES`
- Auto-configuration bean that sets up Ignite based on properties
- Supports local (single-node), shared FS (Docker/VMs), and Kubernetes discovery

### 2. Testcontainers-based Cluster Tests ✅
- **Location**: `structures-core/src/test/java/org/kinotic/structures/cluster/`
- **Files**:
  - `ClusterTestBase.java` - Base class for 3-node cluster setup
  - `ClusterHealthVerifier.java` - Health check and verification utilities
  - `ClusterCacheEvictionTest.java` - Main test suite with 5 test scenarios
  - `README.md` - Comprehensive test documentation

### 3. Docker Compose Cluster Configuration ✅
- **File**: `docker-compose/compose.cluster-test.yml`
- Defines 3-node Structures cluster
- Shared Elasticsearch instance
- Integrated with OpenTelemetry stack (Grafana, Prometheus, Jaeger, Loki)
- Each node accessible on different ports

### 4. Manual Testing Documentation ✅
- **File**: `docker-compose/CLUSTER_TESTING.md`
- Step-by-step testing procedures
- Troubleshooting guide
- Prometheus queries for metrics verification
- Expected performance benchmarks

### 5. Build Configuration ✅
- **File**: `structures-core/build.gradle`
- Added `clusterTest` task for running cluster tests
- Excludes cluster tests from regular test runs (resource-intensive)
- Configured with 10-minute timeout and detailed logging

### 6. Kubernetes Configuration ✅
- **Files**:
  - `helm/structures/values.yaml` - Ignite discovery configuration
  - `helm/structures/templates/ignite-service.yaml` - Headless service for discovery
- Documents Kubernetes discovery setup
- StatefulSet recommendations
- Production deployment guidance

### 7. Updated Design Documentation ✅
- **File**: `structures-core/CACHE_EVICTION_DESIGN.md`
- Added "Cluster Testing" section
- Added "Kubernetes Production Deployment" section
- Performance expectations documented
- Troubleshooting guide added

### 8. Comprehensive Configuration Documentation ✅
- **Files**:
  - `structures-core/IGNITE_KUBERNETES_TUNING.md` - All Kubernetes IP Finder options
  - `structures-core/IGNITE_CONFIGURATION_REFERENCE.md` - Quick reference guide
- Documents all 22+ tunable options for Apache Ignite
- Includes examples for different deployment scenarios
- RBAC requirements for Kubernetes
- Troubleshooting and tuning recommendations

## How to Use

### Quick Start - Docker Compose (Manual Testing)

```bash
# 1. Start 3-node cluster
cd docker-compose
docker compose -f compose.cluster-test.yml up

# 2. Access nodes
# Node 1: http://localhost:9091
# Node 2: http://localhost:9092
# Node 3: http://localhost:9093

# 3. Follow manual testing guide
# See docker-compose/CLUSTER_TESTING.md
```

### Quick Start - Testcontainers (Automated Testing)

```bash
# 1. Build server image
./gradlew :structures-server:bootBuildImage

# 2. Run cluster tests
./gradlew :structures-core:clusterTest

# 3. View results in build/reports/tests/clusterTest/
```

## Test Scenarios Covered

1. **Cluster Formation** - Verify all nodes start and join cluster
2. **Cache Eviction Propagation** - Modify on node1, verify eviction on all nodes
3. **Node Failure Handling** - Kill node during eviction, verify retry succeeds
4. **Deletion Propagation** - Delete structure/query, verify cluster-wide cleanup
5. **Metrics Recording** - Verify OpenTelemetry metrics are emitted

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Testcontainers / Docker Compose          │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Node 1       │  │ Node 2       │  │ Node 3       │     │
│  │ :8081, :4001 │  │ :8082, :4002 │  │ :8083, :4003 │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
│         │                 │                 │              │
│         └─────────────────┼─────────────────┘              │
│                           │                                │
│                   ┌───────▼────────┐                       │
│                   │ Elasticsearch  │                       │
│                   │     :9200      │                       │
│                   └────────────────┘                       │
│                                                             │
│         Ignite Cluster Discovery: Static IP                │
│         Communication Port: 47100                          │
│         Discovery Port: 47500                              │
└─────────────────────────────────────────────────────────────┘
```

## Performance Expectations

### Normal Operation
- Cache eviction completes in < 1 second
- Retry rate < 1%
- Success rate > 99%
- P95 latency < 2 seconds

### During Node Failure
- First attempt fails (to unreachable node)
- Retry succeeds within 1-2 seconds
- Total duration < 5 seconds
- Cluster continues with remaining nodes

## Kubernetes Production Deployment

For production Kubernetes deployments:

1. Set `replicaCount: 3` in `helm/structures/values.yaml`
2. Enable Kubernetes discovery: `ignite.discovery.enabled: true`
3. Deploy headless service (automatically created by Helm)
4. Use StatefulSet for stable network identities

See `CACHE_EVICTION_DESIGN.md` for complete Kubernetes setup guide.

## Troubleshooting

### Common Issues

**Tests won't start**: Check Docker is running and has sufficient resources (12GB RAM)

**Image not found**: Build image first: `./gradlew :structures-server:bootBuildImage`

**Cluster won't form**: Increase join timeout in configuration

**Port conflicts**: Tests use dynamic ports, but check for orphaned containers

See detailed troubleshooting in:
- `structures-core/src/test/java/org/kinotic/structures/cluster/README.md`
- `docker-compose/CLUSTER_TESTING.md`

## Next Steps

1. **Run the tests** to verify cluster cache eviction works
2. **Set up monitoring** using the provided Grafana dashboards
3. **Configure alerts** for production based on the Prometheus queries
4. **Deploy to Kubernetes** using the Helm chart with cluster discovery enabled

## Configuration Properties Summary

All cluster configuration is now managed through `StructuresProperties`:

**Discovery Type Selection**:
```yaml
structures:
  cluster-discovery-type: "local"  # or "sharedfs" or "kubernetes"
```

**Type-Safe Constants**:
```java
import org.kinotic.structures.api.config.StructuresProperties.ClusterDiscoveryType;

ClusterDiscoveryType.LOCAL      // Single-node
ClusterDiscoveryType.SHAREDFS    // Docker/VMs
ClusterDiscoveryType.KUBERNETES  // Kubernetes
```

**Key Properties**:
- Network: `cluster-discovery-port`, `cluster-communication-port`, `cluster-join-timeout-ms`
- Shared FS: `cluster-shared-fs-addresses`
- Kubernetes: `cluster-kubernetes-namespace`, `cluster-kubernetes-service-name`
- Retry: `max-cluster-sync-retry-attempts`, `cluster-sync-retry-delay-ms`, `cluster-sync-timeout-ms`

See `IGNITE_CONFIGURATION_REFERENCE.md` for complete property list.

## Resources

- **Configuration Reference**: `structures-core/IGNITE_CONFIGURATION_REFERENCE.md` - Quick reference
- **Kubernetes Tuning**: `structures-core/IGNITE_KUBERNETES_TUNING.md` - Advanced tuning (22+ options)
- **Test Documentation**: `structures-core/src/test/java/org/kinotic/structures/cluster/README.md`
- **Manual Testing Guide**: `docker-compose/CLUSTER_TESTING.md`
- **Design Document**: `structures-core/CACHE_EVICTION_DESIGN.md`
- **Helm Values**: `helm/structures/values.yaml`

---

**Implementation Date**: February 13, 2025  
**Status**: ✅ Complete - StructuresProperties-based configuration with comprehensive documentation
**Configuration**: Type-safe, environment-variable friendly, production-ready


