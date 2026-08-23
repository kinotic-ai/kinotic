# Port the EntityDescriptor cache fix to MindsIgnited/structures

`structures` is the ancestor of kinotic's persistence layer — kinotic's `EntityDefinition` was called
`Structure` there, and vestiges of that naming still survive in kinotic (`structureId`,
`StructureDiscoveryTools`, `structureService`). The same defect almost certainly exists in
`structures`, in the same shape. This is how it was found, fixed, and proven in kinotic, so it can be
repeated rather than rediscovered.

The kinotic fix is commit `cbf26e18b` (27 files) — read it as the reference implementation, but do
not assume the class names match.

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

Move the derived predicates onto it rather than duplicating them, and give the document a single
mapping method (`toDescriptor()`) so the field mapping is written exactly once. Then swap the
retainer fields and build the descriptor in the cache loader after the construction-time walks:

```java
EntityDescriptor entityDescriptor = entityDefinition.toDescriptor();
return authServiceFactory.createEntityDefinitionAuthorizationService(entityDefinition)
                         .map(authService -> new DefaultEntityService(..., entityDescriptor, ...));
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

Measurement matters here because the intuition is wrong in both directions: the cost is dominated by
schema retention on wide entities and barely affected by it on narrow ones.

Write a **throwaway** in-process test (Spring boots the cache in the test JVM; only the datastore
needs to be containerised). For each of two schema shapes — one narrow, ~10 type nodes, one wide,
~300 nodes built by nesting generated sub-objects — publish N=100 definitions, force GC and read used
heap, then load all N into the cache, force GC and read again. Divide the delta by N.

**Run it on the parent commit too.** Post-fix numbers alone prove nothing: "wide costs the same as
narrow" is equally consistent with "the fix worked" and "the probe cannot see schema cost". The
before/after pair is what makes it evidence. Kinotic's result:

```
                                pre-fix    post-fix
  ~11 type nodes               27,955 B    25,169 B
  ~300 type nodes              75,939 B    23,941 B
```

Delete the probe when done — it takes ~6 minutes and creates hundreds of indices per run.

## What to expect, honestly

The narrow case barely moves; roughly 10%, arguably inside the noise band. The entire value is in the
wide case, where it removed 68%, and it is better understood as a bound than a saving: per-entity
cost stops growing with however wide a customer makes their schema. Decide whether `structures` has
customers with wide entities before spending the effort — look at whatever load-generator or sample
entities the repo has, not at the smallest test fixture, which is what produced kinotic's misleadingly
cheap original coefficient.
