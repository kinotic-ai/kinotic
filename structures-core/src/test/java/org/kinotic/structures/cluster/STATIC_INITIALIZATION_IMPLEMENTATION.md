# Static Initialization Implementation Summary

> **⚠️ INTERNAL DOCUMENTATION**
>
> This document is for internal development use only. It describes implementation details of the test infrastructure.
> For user-facing clustering documentation, see:
> - [IGNITE_KUBERNETES_TUNING.md](../../../../../../IGNITE_KUBERNETES_TUNING.md) - Cluster configuration guide
> - [CLUSTER_TESTING.md](../../../../../../../docker-compose/CLUSTER_TESTING.md) - Manual testing guide

---

## Overview

Cluster container startup has been moved from `@BeforeAll` to a static block to ensure proper initialization order:

1. **Elasticsearch** starts first (in `ElasticsearchTestBase` static block)
2. **Cluster containers** start second (in `ClusterTestBase` static block)  
3. **Spring context** initializes third (after static blocks complete)
4. **Tests** run last (after Spring context is ready)

## Java Initialization Order

### Execution Sequence:

```
1. Class Loading Phase:
   ElasticsearchTestBase static block
     ↓ (completes fully)
   ClusterTestBase static block
     ↓ (completes fully)
   ClusterCacheEvictionTest static block (if any)

2. Instance Creation Phase:
   ElasticsearchTestBase constructor
     ↓
   ClusterTestBase constructor
     ↓
   ClusterCacheEvictionTest constructor

3. Spring Context Initialization:
   @SpringBootTest processes
   Spring context loads
   @Autowired fields injected

4. Test Execution Phase:
   @BeforeAll methods
   @Test methods
   @AfterAll methods
```

## Implementation Details

### Static Block in ClusterTestBase

The static block:
1. **Verifies Elasticsearch is ready** - Checks parent static variables are initialized
2. **Reads configuration** - From system properties (can't use Spring @Autowired in static block)
3. **Starts cluster containers** - Docker Compose containers start here
4. **Registers shutdown hook** - Safety net for cleanup

### Configuration Reading

Since static blocks run before Spring context loads, we can't use `@Autowired TestProperties`. Instead:

- **Reads from system properties** with defaults:
  - `structures.test.cluster.useExternal` (default: `false`)
  - `structures.test.cluster.nodeCount` (default: `4`)

- **Spring loads actual config** from `application-test.yml` later via `TestProperties`

- **System properties override** - Can be set via Gradle test system properties if needed

### Cleanup Strategy

Two cleanup mechanisms:
1. **@AfterAll method** - Primary cleanup (explicit, controlled)
2. **JVM shutdown hook** - Safety net (runs on JVM shutdown)

## Benefits

✅ **Guaranteed order** - Elasticsearch → Cluster → Spring context  
✅ **Fail fast** - If startup fails, class won't load (before tests run)  
✅ **Clean separation** - Infrastructure setup in static blocks, test logic in @BeforeAll  
✅ **Resource safety** - Multiple cleanup mechanisms ensure containers stop

## Configuration

### Default Behavior
- Reads from `application-test.yml` via Spring `TestProperties` (after Spring loads)
- Static block uses system properties with defaults matching YAML

### Override via System Properties
```bash
# In gradle.properties or test command
-Dstructures.test.cluster.useExternal=true
-Dstructures.test.cluster.nodeCount=4
```

## Testing the Order

Look for these log messages to verify order:

```
[ElasticsearchTestBase] Starting Elasticsearch Test Container...
[ElasticsearchTestBase] Elasticsearch Test Container Started
[ClusterTestBase] Static block - START
[ClusterTestBase] Elasticsearch ready at ...
[ClusterTestBase] Starting Docker Compose cluster containers...
[ClusterTestBase] Docker compose environment started using ...
[ClusterTestBase] Static block - END
[ClusterTestBase] Cluster setup - waiting for cluster formation
```

## Troubleshooting

### Issue: Containers not starting
- Check Elasticsearch started successfully (look for parent static block completion)
- Verify Docker is running
- Check compose file exists at expected path

### Issue: Configuration mismatch
- Static block reads from system properties
- Spring loads from `application-test.yml`
- If they differ, Spring config takes precedence for runtime behavior
- Static block config only affects container startup

### Issue: Containers not cleaned up
- Check `@AfterAll` method executes
- Shutdown hook should catch JVM shutdown cases
- Verify no exceptions in cleanup code

