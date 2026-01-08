# Java Initialization Order Plan for Cluster Tests

> **⚠️ INTERNAL DOCUMENTATION**
>
> This document is for internal development use only. It describes the design and implementation planning for test infrastructure initialization order.
> For user-facing clustering documentation, see:
> - [IGNITE_KUBERNETES_TUNING.md](../../../../../../IGNITE_KUBERNETES_TUNING.md) - Cluster configuration guide
> - [CLUSTER_TESTING.md](../../../../../../../docker-compose/CLUSTER_TESTING.md) - Manual testing guide

---

## Problem Statement

We need to ensure proper initialization order:
1. **Elasticsearch** must start first (already in static block)
2. **Cluster containers** must start after Elasticsearch but before Spring context loads
3. Both must be ready before `@SpringBootTest` processes and Spring context initializes

## Java Initialization Order (Inheritance)

When `ClusterCacheEvictionTest` (child) extends `ClusterTestBase` (parent) extends `ElasticsearchTestBase` (grandparent):

### Execution Order:

1. **Class Loading Phase** (happens when class is first referenced):
   ```
   ElasticsearchTestBase static block executes
     ↓ (completes fully)
   ClusterTestBase static block executes  
     ↓ (completes fully)
   ClusterCacheEvictionTest static block executes (if any)
   ```

2. **Instance Creation Phase** (happens when test instance is created):
   ```
   ElasticsearchTestBase instance initializers
   ElasticsearchTestBase constructor
     ↓
   ClusterTestBase instance initializers  
   ClusterTestBase constructor
     ↓
   ClusterCacheEvictionTest instance initializers
   ClusterCacheEvictionTest constructor
   ```

3. **Test Execution Phase**:
   ```
   @BeforeAll methods (setupCluster)
   @Test methods
   @AfterAll methods (teardownCluster)
   ```

## Current State

- ✅ **ElasticsearchTestBase**: Has static block that starts Elasticsearch container
- ❌ **ClusterTestBase**: Cluster containers start in `@BeforeAll` method (too late!)
- ❌ **Problem**: Spring context loads before cluster containers are ready

## Solution Plan

### Step 1: Move Cluster Container Startup to Static Block

**Location**: `ClusterTestBase.java`

**Changes**:
- Add a static block that starts cluster containers
- Ensure it runs AFTER parent static block completes (guaranteed by Java)
- Verify Elasticsearch is ready before starting cluster containers

### Step 2: Ensure Elasticsearch is Ready

**Verification Strategy**:
- Static blocks execute sequentially, so Elasticsearch static block completes first
- However, we should verify Elasticsearch container is actually running before starting cluster containers
- Add a health check or wait mechanism

### Step 3: Update @BeforeAll Method

**Changes**:
- Remove container startup logic from `setupCluster()`
- Keep only cluster formation waiting logic
- Containers should already be running from static block

### Step 4: Handle Cleanup

**Options**:
- **Option A**: Use `@AfterAll` to stop containers (current approach)
- **Option B**: Use JVM shutdown hook in static block
- **Recommendation**: Keep `@AfterAll` for explicit control

## Implementation Details

### Static Block Structure

```java
static {
    // 1. Verify Elasticsearch is ready (from parent static block)
    if (ELASTICSEARCH_CONTAINER == null && !useExternalElasticsearch) {
        throw new IllegalStateException("Elasticsearch container not initialized");
    }
    
    // 2. Wait for Elasticsearch to be healthy (if needed)
    // Optional: Add health check here
    
    // 3. Start cluster containers
    if (!USE_EXTERNAL) {
        // Start Docker Compose containers
        environment = new DockerComposeContainer<>(...);
        environment.start();
    }
}
```

### Key Considerations

1. **Static Variable Access**: 
   - Child static block can access parent static variables (`elasticsearchPort`, `elasticsearchHost`)
   - These are guaranteed to be initialized because parent static block runs first

2. **Exception Handling**:
   - Static blocks can throw exceptions
   - If cluster startup fails in static block, test class won't load
   - This is actually good - fail fast before tests run

3. **Logging**:
   - Static blocks run before SLF4J is configured
   - Use `System.out.println` or ensure logging is configured early

4. **Resource Cleanup**:
   - Static blocks can't use `@AfterAll` directly
   - Need to use shutdown hooks or keep `@AfterAll` method
   - Recommendation: Use `@AfterAll` for cleanup (more explicit)

## Implementation Steps

1. ✅ Document initialization order (this document)
2. ⏳ Move cluster container startup to static block in `ClusterTestBase`
3. ⏳ Add Elasticsearch readiness verification
4. ⏳ Update `@BeforeAll` to only wait for cluster formation
5. ⏳ Test initialization order
6. ⏳ Verify cleanup works correctly

## Testing the Order

Add logging to verify execution order:

```java
// ElasticsearchTestBase static block
System.out.println("[1] ElasticsearchTestBase static block - START");

// ClusterTestBase static block  
System.out.println("[2] ClusterTestBase static block - START");

// @BeforeAll method
log.info("[3] @BeforeAll setupCluster - START");
```

Expected output:
```
[1] ElasticsearchTestBase static block - START
[1] ElasticsearchTestBase static block - END
[2] ClusterTestBase static block - START
[2] ClusterTestBase static block - END
[3] @BeforeAll setupCluster - START
```

## Risks and Mitigations

### Risk 1: Static Block Exception
- **Risk**: If static block throws exception, class won't load
- **Mitigation**: This is actually desired - fail fast before tests run

### Risk 2: Elasticsearch Not Ready
- **Risk**: Cluster containers start before Elasticsearch is fully ready
- **Mitigation**: Add health check or wait in static block

### Risk 3: Resource Leaks
- **Risk**: Containers not cleaned up if test fails
- **Mitigation**: Use `@AfterAll` with try-finally, or shutdown hook

### Risk 4: Multiple Test Classes
- **Risk**: Static blocks run once per class, but containers might be shared
- **Mitigation**: Use singleton pattern or check if containers already running

## Alternative Approaches Considered

### Option A: Static Block (Recommended ✅)
- **Pros**: Guaranteed order, runs before Spring context
- **Cons**: Less flexible, harder to debug

### Option B: @BeforeAll with Synchronization
- **Pros**: More flexible, easier to debug
- **Cons**: Runs after Spring context starts (too late for our use case)

### Option C: Testcontainers Singleton Pattern
- **Pros**: Shared containers across tests
- **Cons**: More complex, potential state issues

## Conclusion

**Recommended Approach**: Move cluster container startup to static block in `ClusterTestBase`, ensuring it runs after Elasticsearch static block completes but before Spring context initialization.

