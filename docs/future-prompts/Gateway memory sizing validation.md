# Gateway memory sizing — what was measured, and what still needs validating

We built a container-sizing model for kinotic-server from measurements taken against a running
gateway, and published it as an artifact. This is the handoff: how the numbers were collected so they
can be re-derived or challenged, what the model deliberately does not cover, and the Elasticsearch
question that sits underneath all of it.

**The artifact:** https://claude.ai/code/artifact/2b6a153e-88ea-4375-bd1f-6f2a45e01b6c — inputs are
organizations, projects per org (×2 workload replicas applied automatically), active users per org,
services per project, entity definitions per project, named queries per project, the in-flight event
budget, and vCPUs. It outputs the container limit plus the settings to configure, and shows every
term in a stacked allocation bar.

## How the numbers were collected

Every per-unit figure is a **marginal** cost: two runs differing only in the quantity being varied,
the difference divided by that quantity. Nothing is estimated or reasoned from source. The
instruments were NMT (`-XX:NativeMemoryTracking=summary`) for off-heap, live `jcmd` heap readings
after a forced GC, Ignite `DataRegionMetrics.getTotalAllocatedSize()` for the session and
subscription caches, and live-objects-only heap dumps (`GC.heap_dump -all=false`) for retention
questions. All probes were throwaway and have been deleted.

| Quantity | Amount | Sample it came from |
|---|---|---|
| Bare connection, heap | 18,334 B | 36.7 MiB across 2,000 connections |
| Reply destination + RPC, heap | 4,618 B | UI run minus bare run, both at 2,000 |
| Published service subscription, heap | 1,036 B | 2,000 connections × 50 services vs 2,000 bare |
| User connection, Ignite off-heap | 1,064 B | 10.15 MiB across 10,000 connections |
| Service address, first registration | 344 B | 2,000 fresh addresses |
| Service address, each replica after | 220 B | second registration on those same 2,000 |
| Session, scoped participant | 588 B | 518 B serialized exactly + 70 B Ignite entry overhead |
| Ignite caches, empty | 16.1 MiB | region allocated at zero connections |
| Netty retained, per magazine | 312 KiB | 9.76 MiB flat at 1k/5k/10k, 16 vCPUs → 32 magazines |
| Cached `EntityService`, heap | 24,000 B | 100 definitions each of an 11-node and a ~300-node schema: 25,169 B and 23,941 B |
| Cached `QueryExecutor`, heap | 285 B | 114,000 B across 400 executors |
| Metaspace | 232.5 MiB | printed by the Paketo memory calculator at 39,623 classes |

## Findings that shaped the model

**Direct memory does not scale with connections.** It was byte-identical (9,992 KB) at 1,000, 5,000
and 10,000 connections, because Netty's retained chunks live in magazines bounded by
`MAX_STRIPES = availableProcessors() * 2`, not by socket count. Direct memory is a function of vCPUs
and in-flight frame size only.

**The WebSocket frame limit is deliberately tied to `maxEventPayloadSize`.** Splitting them was tried
and reverted: the TS client sends one WebSocket frame per STOMP frame, and `@stomp/stompjs` only
chunks string bodies (`typeof rawChunk !== 'string' || !this.splitLargeFrames`) while we publish
`binaryBody`, so the chunking path is unreachable. A smaller frame limit would just be a smaller
payload limit under a different name. The default is now 2 MB rather than 100 MB, which is what makes
the direct-memory budget tractable.

**There is no second limit behind the frame limit.** `maxWebSocketMessageSize` is never enforced,
because vertx-stomp-lite reads raw frames via `socket.handler(...)` instead of setting a
`textMessageHandler`/`binaryMessageHandler`, so Vert.x never installs its `FrameAggregator`.

**Exhausting direct memory fails quietly** — verified by forcing it. Netty throws inside the frame
decoder, the connection is closed with **no STOMP ERROR frame**, the JVM does **not** exit (that
error is thrown by library code, so `-XX:+ExitOnOutOfMemoryError` never fires), `/health` keeps
returning 204, and connections that were idle and sending nothing get dropped too. See the alert item
in NavidNotes.

**The Ignite region is a ceiling, not a reservation.** It is malloc'd and only becomes resident as it
fills, so an oversized cap costs no RSS. Head room should be sized on projected use, not on the cap.

**Sharding does not divide the cost evenly.** `__vertx.subs` is `REPLICATED`, so subscriptions are
held in full on every node; sessions are `PARTITIONED` with one backup and cost roughly `2/N`.

**Both persistence caches are bounded**, so definition count cannot run the heap away — it starts
thrashing instead. Both caps are properties (`kinotic.persistence.entityServiceCacheMaxSize` and
`namedQueriesCacheMaxSize`), each defaulting to 10,000, both with a 20-hour idle expiry.

**The `EntityService` coefficient is flat in schema size**, as of the `EntityDescriptor` change. A
cached `EntityService` used to retain the whole `EntityDefinition` — including the `ObjectC3Type`
schema and the `decoratedProperties` index — for the life of the cache entry, though nothing on the
request path read either after construction. It now holds a nine-scalar `EntityDescriptor`. Measured
before and after, at 100 definitions per shape:

| Schema shape | Before | After |
|---|---|---|
| 11 type nodes, like `Person` | 27,955 B | 25,169 B |
| ~300 type nodes, like `Provider` | 75,939 B | 23,941 B |

The narrow case barely moves, which is the point: the old coefficient was measured on the smallest
schema in the repo, so it never showed the term that scaled.

## What needs improving or validating

1. **Nothing above 10,000 connections was measured.** A JVM on macOS is hard-capped at 10,240 file
   descriptors (`max_fd=10240` even with `ulimit -n 200000`), and Docker Desktop's port-forwarding
   proxy stalls around 16,000. Validating 50k+ needs a Linux host with the load generator and the
   gateway on the same network. Direct memory should stay flat (it is core-bound); heap is the one to
   confirm.
2. **Heap per connection was still converging** — 18.7 → 24.7 → 25.4 KiB at 1k/5k/10k. Treat 25.4 KiB
   as a floor, not a settled asymptote.
3. **The "% of connections at once" input is a pure planning assumption.** Nothing in the platform
   bounds concurrent in-flight events; the only thing that stops it is `MaxDirectMemorySize` itself.
   Replace the guess with a real reading of `jvm.buffer.memory.used{pool=direct}` under production
   load. The OTEL flag that emits it is already set in `compose.kinotic-server.yml`.
4. **The 130 MiB baseline heap** is an idle full-profile server on a workstation. Re-measure on a real
   deployment.
5. **The model sizes one instance.** No node count is modelled; see the replication asymmetry above
   before extrapolating.
6. **Users are not connections.** The coefficient is per connection and the input is "active users" —
   a user with three tabs is three connections. A tabs-per-user multiplier may be worth adding.
7. **The session term assumes 30-minute `ACTIVITY` sessions.** Related and unverified: the WebSocket
   upgrade emits no `Set-Cookie`, which raises whether `session.setAccessed()` on the WS path is ever
   flushed to the clustered store. If it is not, `ACTIVITY` sessions could expire under an active
   connection. Noticed, not chased.
8. **The named-query coefficient used 400 identical short statements.** Real queries carry longer SQL
   and will cost proportionally more.
9. ~~**The entity-definition coefficient used the `Person` sample schema.**~~ Resolved. A wide schema
   did cost more — 2.7× — and the cause was schema retention, now removed. The coefficient is flat in
   schema size; see the before/after table above.

## The Elasticsearch concern — not modelled at all

**Every `EntityDefinition` creates a 9-shard index.** This surfaced by accident: 120 definitions blew
straight through Elasticsearch's default 1,000-shard cluster cap mid-measurement, and
`cluster.max_shards_per_node` had to be raised to 20,000 just to finish the run.

At the artifact's defaults — 10 orgs × 2 projects × 10 definitions = 200 definitions — that is
**1,800 shards**, already past what a default single-node cluster will accept. At 100 definitions per
project it is 18,000.

Nothing in the sizing model accounts for Elasticsearch memory at all, and shards carry real per-shard
heap on the ES side. The questions worth answering:

- Is 9 shards per index deliberate, or an inherited default? Should it scale with expected data
  volume per entity rather than being fixed?
- Should entity definitions share indices instead of each getting their own?
- What is the ES-side heap and shard budget per org/project, and should the sizing model cover the ES
  tier as well as the gateway?
- What happens to a customer creating their 10,001st entity definition — `entityServiceCacheMaxSize`
  defaults to 10,000, so it starts evicting and reloading. Is that a cliff we should surface?

**Also found while measuring:** named queries only support aggregates.
`DefaultQueryExecutorFactory:79` throws `NotImplementedException` for `SELECT`, `UPDATE`, `DELETE` and
`INSERT`. And the aggregate detection regex in `QueryUtils` is
`\b(AVG|COUNT|...)\s*\([a-zA-Z0-9_.,='() ]+\)` — the character class excludes `*`, so `COUNT(*)` is
classified as a plain SELECT and rejected, while `COUNT(id)` works. That is likely a bug in its own
right.
