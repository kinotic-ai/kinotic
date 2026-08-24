# Port the EntityDescriptor cache fix to MindsIgnited/structures

`structures` is the ancestor of kinotic's persistence layer — kinotic's `EntityDefinition` was called
`Structure` there, and vestiges of that naming still survive in kinotic (`structureId`,
`StructureDiscoveryTools`, `structureService`). The same defect almost certainly exists in
`structures`, in the same shape. This is how it was found, fixed, and proven in kinotic, so it can be
repeated rather than rediscovered.

The kinotic fix is commit `cbf26e18b` — read it as the reference implementation, but do not assume
the class names match. Its shape, for scale:

```
 1  new record                         EntityDescriptor
 1  document                           EntityDefinition — 3 predicates out, toDescriptor() in
 4  retainers                          field type swap + accessor rename
 6  SPI files                          dead parameter deleted
 8  hooks / pre-processors             parameter type swap
 5  sql executors + factory            parameter type swap
 2  caches                             descriptor built; second cache key changed
 26 files, ~240 lines, 0 test changes
```

No test needed changing, which is the signal that this is a pure retention fix and not a behaviour
change. If tests start failing in `structures`, something in the union from Step 1 was missed.

## The defect

A cache of long-lived, per-entity service objects retains the entire authored schema document, while
nothing on the request path reads that document after the service is constructed.

In kinotic, `EntityServiceCache` built a `DefaultEntityService` per published `EntityDefinition` and
handed it the whole definition:

```java
// EntityServiceCache.java, before
return authServiceFactory.createEntityDefinitionAuthorizationService(entityDefinition)
                         .map(authService -> new DefaultEntityService(..., entityDefinition, ...));
```

```java
// DefaultEntityService.java, before
private final EntityDefinition entityDefinition;   // holds schema + decoratedProperties for 20 hours
```

The loader reads the full document with no source filtering, so `schema` (the whole `ObjectC3Type`
graph) and `decoratedProperties` are both populated and both pinned for the life of the cache entry.
Both are consumed exactly once during construction — `decoratedProperties` to build the field
pre-processor map, `schema` inside the authorization-service constructor — and never touched again.

## Step 1 — confirm it exists there, and find the real cut

Two greps do the whole diagnosis. Substitute the local type name for `EntityDefinition`.

```bash
# every class that RETAINS the document as a field — these are the offenders
grep -rn "EntityDefinition [a-zA-Z]*;" --include="*.java" */src/main

# every member actually read, per file — the union across the retainers is your descriptor
grep -rnoE "entityDefinition\.(get|is)[A-Za-z]+\(\)" --include="*.java" . \
  | awk -F: '{split($1,p,"/"); print p[length(p)]"  "$3}' | sort -u
```

In kinotic the retainers were the entity service, two upsert pre-processors, and the SQL query
executor base, and the union came to **nine scalars** — id, organizationId, applicationId, name,
index name, tenancy mode, and the tenant/version/time-reference field names. If the union in
`structures` is similarly small, the same fix applies. If some retainer genuinely needs the schema on
the request path, stop and reassess — that changes the problem.

Note that `grep` for `.getX()` misses derived predicates like `isStream()` /
`isMultiTenantSelectionEnabled()`. Include `is[A-Z]` as above, and check their call sites too.

## Step 2 — delete dead parameters first

Before touching anything structural, look for signatures that pass the document and ignore it. In
kinotic the whole field-pre-processor SPI took one and no implementation read it:

```java
R process(EntityDefinition entityDefinition, String fieldName, D decorator, T value, EntityContext context);
//        ^ all five implementations ignored this
```

Deleting it first removed five signatures from the blast radius of the real change, for free.

## Step 3 — the descriptor

One new value object, holding the union from Step 1. Name it for the local convention: in kinotic,
`ServiceDescriptor`/`ServiceDefinition` already established that `*Definition` is the declarative,
serializable contract and `*Descriptor` is the node-local operational view, so `EntityDescriptor` was
the house-consistent name. Check what `structures` already does before picking.

```java
@Builder
public record EntityDescriptor(String id, String organizationId, String applicationId, String name,
                               String itemIndex, MultiTenancyType multiTenancyType,
                               String tenantIdFieldName, String versionFieldName,
                               String timeReferenceFieldName) {

    public boolean isOptimisticLockingEnabled(){ return versionFieldName != null; }
    public boolean isMultiTenantSelectionEnabled(){ return tenantIdFieldName != null; }
    public boolean isStream(){ return timeReferenceFieldName != null; }
}
```

The naming was settled by precedent rather than taste — the ancestor codebase already documents the
distinction, so check for the equivalent before inventing a name:

```java
// kinotic-core/.../api/service/ServiceDescriptor.java
/**
 * This is the node-local, invocable view of a service, holding live {@link FunctionDescriptor}s; the
 * declarative, serializable contract view is {@code ServiceDefinition} in the service directory.
 */
```

Move the derived predicates onto the descriptor rather than duplicating them, and give the document a
single mapping method so the field mapping is written exactly once:

```java
// EntityDefinition.java
- @JsonIgnore
- public boolean isOptimisticLockingEnabled(){ return versionFieldName != null; }
- @JsonIgnore
- public boolean isMultiTenantSelectionEnabled(){ return tenantIdFieldName != null; }
- @JsonIgnore
- public boolean isStream(){ return timeReferenceFieldName != null; }
+ @JsonIgnore
+ public EntityDescriptor toDescriptor(){
+     return EntityDescriptor.builder().id(id).organizationId(organizationId) /* ...all nine... */ .build();
+ }
```

Callers of the moved predicates on the document side hoist a local rather than chaining
`x.toDescriptor().isStream()` at each use — in kinotic there were six, all on publish/update paths:

```java
// DefaultEntityDefinitionService.java — built AFTER the setters above it, see "Things that will bite"
+ EntityDescriptor existingDescriptor = existingEntityDefinition.toDescriptor();
+ EntityDescriptor descriptor = entityDefinition.toDescriptor();

- if (!existingEntityDefinition.isMultiTenantSelectionEnabled() && entityDefinition.isMultiTenantSelectionEnabled()
+ if (!existingDescriptor.isMultiTenantSelectionEnabled() && descriptor.isMultiTenantSelectionEnabled()
```

Then the retainers, and the cache loader — where the document becomes unreachable:

```java
// DefaultEntityService.java, AbstractJsonUpsertPreProcessor.java,
// MapUpsertPreProcessor.java, AbstractQueryExecutor.java
- private final EntityDefinition entityDefinition;
+ private final EntityDescriptor entityDescriptor;
```

```java
// EntityServiceCache.java — after the decoratedProperties walk and before the auth service call
+ EntityDescriptor entityDescriptor = entityDefinition.toDescriptor();

  return authServiceFactory.createEntityDefinitionAuthorizationService(entityDefinition)
                           .map(authService -> new DefaultEntityService(
                                   authService, crudServiceTemplate,
-                                  new DelegatingUpsertPreProcessor(..., entityDefinition, fieldPreProcessors),
+                                  new DelegatingUpsertPreProcessor(..., entityDescriptor, fieldPreProcessors),
                                   esAsyncClient, namedQueriesService, jsonMapper, readPreProcessor,
-                                  entityDefinition,
+                                  entityDescriptor,
                                   persistenceProperties));
```

Both heavyweight members are fully consumed by the time `.map(...)` runs — `decoratedProperties` by
the field-pre-processor walk just above, `schema` inside the authorization-service constructor — so
after this returns nothing reachable holds the graph.

And the second cache's key, which otherwise inherits ownership of every graph:

```java
// DefaultNamedQueriesService.java
- private record CacheKey(String queryName, EntityDefinition entityDefinition) {}
+ private record CacheKey(String queryName, EntityDescriptor entityDescriptor) {}
```

### The mechanical part

Most of the edit is a rename: every body already reads the document through accessors the descriptor
also has, so only the receiver and the accessor style change. Kinotic's document used Lombok
`getX()` while the descriptor is a record with `x()`. This script did the swap across all retainers
in one pass — order matters, accessors are rewritten while the receiver still has its old name:

```python
import pathlib, re, sys

GETTERS = {                       # EntityDefinition getter -> EntityDescriptor record accessor
    "getId": "id", "getName": "name", "getItemIndex": "itemIndex",
    "getMultiTenancyType": "multiTenancyType", "getTenantIdFieldName": "tenantIdFieldName",
    "getVersionFieldName": "versionFieldName", "getTimeReferenceFieldName": "timeReferenceFieldName",
}

for f in sys.argv[1:]:
    p = pathlib.Path(f); s = p.read_text(); orig = s
    # accessor calls first, while the receiver is still named entityDefinition
    for g, a in GETTERS.items():
        s = s.replace(f"entityDefinition.{g}()", f"entityDefinition.{a}()")
    # types, identifiers, javadoc links
    s = s.replace("EntityDefinition entityDefinition", "EntityDescriptor entityDescriptor")
    s = s.replace("entityDefinition", "entityDescriptor")
    s = s.replace("{@link EntityDefinition}", "{@link EntityDescriptor}")
    s = s.replace("import org.kinotic.persistence.api.model.EntityDefinition;",
                  "import org.kinotic.persistence.api.model.EntityDescriptor;")
    if s != orig:
        p.write_text(s)
        print(f"  rewrote {f}")
```

Feed it the retainer files, then handle the cache and any cache keys by hand. In kinotic that was 26
files changed, ~240 lines, and the compiler found every remaining site.

### Order of operations

Work in this order and compile between each — the tree does not compile in the middle of step 3b,
which is expected, because removing the predicates from the document necessarily breaks the retainers
until they switch over.

1. Delete dead parameters (Step 2). **Compiles.**
2. Add the descriptor; move the predicates onto it; add `toDescriptor()`; fix the predicate call
   sites on the document side and any Javadoc links. **Does not compile** — retainers still call the
   moved predicates.
3. Run the swap script over the retainers; build the descriptor in the cache loader; change the
   second cache's key. **Compiles.**
4. Build the whole project including tests — the document appears in public service signatures, so
   other modules will surface here.
5. Run the full integration suite. In kinotic this was 111 tests, unchanged, no test edits needed.

### Verify the invariant afterward

The same grep from Step 1 must now come back empty:

```bash
grep -rn "EntityDefinition [a-zA-Z]*;" --include="*.java" */src/main
# no results = nothing long-lived retains the document
```

Then check what references survive in the former retainers — they should be prose only:

```bash
grep -n "EntityDefinition" <each former retainer>
# expect: log messages, comments, repository method names. No types, no fields.
```

## Things that will bite

- **Type the retainer fields as the concrete descriptor**, never as an interface the document also
  implements. Reaching for a shared interface to home the predicates is tempting; it also makes
  handing back the full document a legal move again, and the compiler stops protecting the invariant.
- **A second cache probably holds the document too.** In kinotic the named-query cache key was
  `record CacheKey(String queryName, EntityDefinition entityDefinition)`. Changing only the entity
  service just moves ownership of the graphs to that cache — search for every cache, key, and map
  whose type parameters mention the document.
- **Build the descriptor after any code that mutates the document.** The publish/update path sets
  version/tenant/time-reference field names partway through; a descriptor built above those setters
  silently captures nulls.
- **Public Javadoc keeps referring to the document.** Notes like "only allowed if multi-tenant
  selection is enabled" describe the caller's contract, which is the authored document, not an
  internal projection. Do not mechanically rewrite those links to the descriptor.
- **Do not trim identity fields as YAGNI.** `organizationId` and `applicationId` look unused by the
  entity service but the named-query repository lookup needs them.

## Step 4 — prove it, with a controlled experiment

Measurement matters here because intuition is wrong in both directions: the cost is dominated by
schema retention on wide entities and barely affected by it on narrow ones. Do not skip this and do
not trust a post-fix-only reading.

### The method

For each of two schema shapes, publish N=100 definitions, force GC and read used heap, then load all
N into the cache, force GC and read again. Divide the delta by N. The two shapes:

- **narrow** — ~10 type nodes, mirroring the smallest real entity in the repo
- **wide** — ~300 type nodes, built by generating nested sub-objects (see `wideSchema` below)

Run three passes in one test: a warmup, then narrow, then wide. The warmup absorbs JIT and
class-loading and its number is discarded.

### Run it on the parent commit too

Post-fix numbers alone prove nothing: "wide costs the same as narrow" is equally consistent with
"the fix worked" and "the probe cannot see schema cost". The before/after pair is the evidence.

Use a detached worktree rather than `git stash`, so the working tree is never disturbed — a stash
mid-run is easy to lose track of, and a GUI client may apply it underneath you:

```bash
git worktree add --detach /tmp/baseline <parent-commit>
cp <probe> /tmp/baseline/<same path>
cd /tmp/baseline && ./gradlew :<test-module>:test --tests "*HeapProbe*"
git worktree remove --force /tmp/baseline
```

Kinotic's result — the pre-fix spread is what validates the post-fix flatness:

```
                                pre-fix    post-fix
  ~11 type nodes               27,955 B    25,169 B
  ~300 type nodes              75,939 B    23,941 B
                               2.7x spread   flat
```

Interpret it as a controlled experiment: between the narrow and wide runs the *only* variable is
schema shape — same authorization service, same pre-processors, same cache-entry overhead. So the
pre-fix 48 KB that varied with shape was the retained document, by elimination. Anything else built
from the schema is unchanged code across both commits and would still show up post-fix.

### Two blockers you will hit

**The per-test timeout.** kinotic has a centralized JUnit per-test timeout (120s); publishing 300
definitions blows straight through it and the run dies with a `TimeoutException` that looks like a
hang. Override it on the method: `@Timeout(value = 40, unit = TimeUnit.MINUTES)`.

**The datastore shard cap.** Every published definition creates an index. Elasticsearch defaults to
1,000 shards per node, and at default shard/replica settings a few hundred definitions exceed it
mid-run. Force single-shard, zero-replica for the probe:

```java
@TestPropertySource(properties = {"kinotic.persistence.numberOfShards=1",
                                  "kinotic.persistence.numberOfReplicas=0"})
```

If `structures` has no such properties, raise `cluster.max_shards_per_node` on the test cluster
instead.

### The probe, as it ran

Adapt names and delete when done — it takes ~6 minutes and creates hundreds of indices per run. Note
`usedHeapAfterGc()`: a single `System.gc()` is not enough, it loops until two consecutive readings
agree.

```java
package org.kinotic.test.probe;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import org.kinotic.idl.api.schema.ArrayC3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.idl.decorators.AutoGeneratedIdDecorator;
import org.kinotic.persistence.api.model.idl.decorators.EntityDecorator;
import org.kinotic.persistence.api.model.idl.decorators.MultiTenancyType;
import org.kinotic.persistence.api.model.idl.decorators.NestedDecorator;
import org.kinotic.persistence.api.services.EntityDefinitionService;
import org.kinotic.persistence.internal.api.services.EntityServiceCache;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

/**
 * THROWAWAY. Measures the marginal heap of one cached EntityService, for two schema shapes.
 */
@Slf4j
@TestPropertySource(properties = {"kinotic.persistence.numberOfShards=1",
                                  "kinotic.persistence.numberOfReplicas=0"})
public class EntityServiceHeapProbe extends KinoticTestBase {

    private static final int N = 100;

    @Autowired private EntityDefinitionService entityDefinitionService;
    @Autowired private EntityServiceCache entityServiceCache;

    /** Person shape: 9 leaf fields / 11 type nodes, matching load-generator Person. */
    private ObjectC3Type narrowSchema(String name){
        return new ObjectC3Type()
                .setName(name).setNamespace("probe")
                .addProperty("id", new StringC3Type(), List.of(new AutoGeneratedIdDecorator()))
                .addProperty("firstName", new StringC3Type())
                .addProperty("lastName", new StringC3Type())
                .addProperty("birthDate", new StringC3Type())
                .addProperty("age", new StringC3Type())
                .addProperty("address", new ObjectC3Type().setName(name + "Address")
                        .addProperty("street", new StringC3Type())
                        .addProperty("city", new StringC3Type())
                        .addProperty("state", new StringC3Type())
                        .addProperty("zip", new StringC3Type()))
                .addDecorator(new EntityDecorator().setMultiTenancyType(MultiTenancyType.NONE));
    }

    /** Provider shape: ~257 leaf fields / ~296 type nodes, matching load-generator Provider+Qualifications. */
    private ObjectC3Type wideSchema(String name){
        ObjectC3Type ret = new ObjectC3Type()
                .setName(name).setNamespace("probe")
                .addProperty("id", new StringC3Type(), List.of(new AutoGeneratedIdDecorator()));
        // 12 nested groups of 4 sub-objects of 5 fields = 240 leaves + 12 top-level scalars
        for (int g = 0; g < 12; g++) {
            ObjectC3Type group = new ObjectC3Type().setName(name + "Group" + g);
            for (int s = 0; s < 4; s++) {
                ObjectC3Type sub = new ObjectC3Type().setName(name + "Sub" + g + "_" + s);
                for (int f = 0; f < 5; f++) {
                    sub.addProperty("field" + f, new StringC3Type());
                }
                group.addProperty("sub" + s, new ArrayC3Type().setContains(sub), List.of(new NestedDecorator()));
            }
            ret.addProperty("group" + g, group);
            ret.addProperty("scalar" + g, new StringC3Type());
        }
        return ret.addDecorator(new EntityDecorator().setMultiTenancyType(MultiTenancyType.NONE));
    }

    private long usedHeapAfterGc() throws Exception {
        long prev = -1, cur = 0;
        for (int i = 0; i < 12 && prev != cur; i++) {   // settle: repeat until two reads agree
            prev = cur;
            System.gc();
            Thread.sleep(300);
            cur = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 10;
        }
        return cur;
    }

    private void measure(String label, boolean wide) throws Exception {
        long t0 = System.currentTimeMillis();
        String prefix = (wide ? "wide" : "narrow") + System.nanoTime() % 100000;
        for (int i = 0; i < N; i++) {
            String n = prefix + "e" + i;
            EntityDefinition def = new EntityDefinition()
                    .setName(n).setApplicationId(TEST_APP_ID).setProjectId(TEST_APP_ID)
                    .setOrganizationId(TEST_ORG_ID)
                    .setSchema(wide ? wideSchema(n) : narrowSchema(n));
            EntityDefinition created = runAsOrganization(() -> entityDefinitionService.create(def)).await();
            runAsOrganization(() -> entityDefinitionService.publish(created.getId())).await();
            if (i % 25 == 0) log.error("PROBE[{}] published {}/{}", label, i, N);
        }
        long before = usedHeapAfterGc();

        for (int i = 0; i < N; i++) {
            String id = prefix + "e" + i;
            runAsOrganization(() -> entityServiceCache.get(TEST_ORG_ID,
                    org.kinotic.persistence.internal.utils.PersistenceUtil
                            .createEntityDefinitionId(TEST_ORG_ID, TEST_APP_ID, id))).await();
        }
        long after = usedHeapAfterGc();

        long deltaKb = after - before;
        log.error("PROBE[{}] N={} beforeKB={} afterKB={} deltaKB={} bytesPerEntityService={} elapsedSec={}",
                  label, N, before, after, deltaKb, (deltaKb * 1024) / N,
                  (System.currentTimeMillis() - t0) / 1000);
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.MINUTES)
    public void measureBothShapes() throws Exception {
        measure("warmup", false);
        measure("narrow", false);
        measure("wide", true);
    }
}
```

## What to expect

The wide column is the one that applies. kinotic's load-generator entities are modeled on real
customer entities, and they are wide — resolved type-node counts, after inlining nested types:

```
  Person            11 nodes     <-- the test fixture, and the reason the original
                                     coefficient looked cheap
  Diagnosis         12
  Product           25
  Treatment         31
  Purchase          47
  Patient           47
  Provider         296           <-- Qualifications alone inlines ~50 nested classes
```

So expect the 68% figure, not the 10% one, and expect it to matter more as customer schemas grow —
the real win is that per-entity cost stops scaling with schema width at all, which turns an unbounded
term into a fixed one.

Two consequences worth carrying into `structures`:

- **Build the wide probe schema to match real entities**, not the test fixture. Measuring against the
  smallest schema in the repo is exactly what hid this defect in kinotic for years.
- **Check the sizing assumptions downstream of this.** Any capacity model built on a coefficient
  measured against a narrow fixture is understating a real deployment, by roughly the ratio of the
  node counts above.
