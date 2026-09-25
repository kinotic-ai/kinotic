# Reconcilable state and coordination — phase plan

Plan of record for giving the platform one shape for stateful records and one way to converge them,
in place of the status field, sweep and conflict rule each record type carries today. Phase 1 is
PR #605, reworked to the shape below before it merges. Everything below was validated against
`develop` at `a298699` (2026-09-23), after PR #588.

Process: one PR per phase, review and merge before the next starts, about ten files per phase. A
phase changes existing code in place; it never adds a parallel copy of something that exists to keep
itself small, and a reshaping of a class or contract together with its callers is a phase of its
own, sized by what the change requires. No phase may rewrite, refactor or restructure what an
earlier phase produced; if a later phase would force that, the earlier phase drew its boundary wrong
and the plan is revisited first.

## The problem, in code

Every status field carries three meanings written by three parties, with nothing to tell them apart:

```java
workload.setStatus(WorkloadStatus.STOPPING);   // DefaultWorkloadOrchestrationService — intent: a caller asked
workload.setStatus(report.getStatus());        // DefaultVmNodeOrchestrationService.applyStatusReport — observation: the node saw
workload.setStatus(WorkloadStatus.FAILED);     // DefaultVmNodeOrchestrationService reaper — inference: the server guessed from silence
```

and the shape is copied, not shared: `WorkloadStatus` + `exitCode`, `VmNodeStatus` + `lastSeen`,
`DeploymentStatus` on four deployables, grind's `ExecutionStatus` on `JobRun` and `TaskRecord` with
no liveness at all, `ServiceDirectoryEntry.online`. Each has its own sweep (a JDK scheduler, a Vert.x
periodic, a `setInterval`, a per-JVM `HashMap`) and its own conflict rule.

What that costs, each traced before this plan was written:

- **The partition trap.** A node silent past `heartbeatTimeoutSeconds` had its workloads written
  `FAILED` on the server's clock; when the node reconnected (which the vm-manager now does on its
  own), its `RUNNING` report carried the older boot timestamp and the clock guard in
  `applyStatusReport` discarded it. The VM ran on, its record said `FAILED`, its room was released,
  and the next push started a second VM beside it. Phase 1 closes this.
- **Lost updates on the workload record.** Every write is a whole-document `saveSync` from the
  writer's own read, so the reaper and the node's report, landing together, each overwrite the
  other's fields. The reservation ledger (PR #588) already answers this for the node record with
  Painless scripts that guard and write in one shard operation; the workload record has no such
  path.
- **Fan-out is per JVM.** `@Consumer` delivers a `GitHubProjectEvent` to every server node
  (`Consumer.java`: "fan-out, never round-robin"); `ProjectDeployOrchestrator` dedupes in a `HashSet`
  on the bean. On the two-node e2e cluster (`compose.kinotic-e2e-test.yml`, `kinotic-server-2`) one
  push runs twice: two `JobRun`s, two rotations of one machine secret, two deploys of one workload id
  onto one checkout. The reaper sweeps from every node the same way.
- **Convergence runs only on a push.** `ensureRuntimeWorkloads` is a reconcile function (keep,
  replace, create, orphan) that runs when GitHub pushes and never otherwise; a crashed microservice VM
  stays down until the next commit. `FrontDoorUiDeploymentProvisioner.schedulePoll` is a per-JVM
  timer that dies with its server, backed by a read-time re-check on listing and a console retry.
- **Runs have no liveness.** `JobRun.nodeId` is recorded and never watched: a run on a server that
  dies stays `RUNNING` forever.
- **No history.** A record says what it is now, never what happened to it, from where, or why. A
  workload found `FAILED` cannot say whether the node reported it, a caller stopped it, or a
  deregistration wrote it.
- **Deletion stops at the module boundary.** Deleting a project deletes its deployment records,
  its machines and its sites, and leaves its VMs running with their room held
  (`DefaultProjectService.java:121-125`: "the runtime workloads on the node are system-side
  resources this management-plane delete cannot reach"). kinotic-system-api depends on
  kinotic-management-api, not the reverse, so the management plane has no call to make; it can only
  write a record.

## The design

The RPC layer became uniform when the framework owned the shape (`@Publish`, `@Proxy`, CRI, `@Scope`)
and modules only declared services. State becomes uniform the same way, as two contracts, one
extending the other.

A **watched** record is one whose state has more than one writer, so each writer owns its own
field or condition entry, every write is one shard operation, and every write goes to the
ledger. A **reconcilable** is a watched record that also carries intent, and a worker converges
the world to it when it is not in its desired state. The three deployments and the node are
reconcilable. A workload and a job run are watched and nothing more: an **artifact**, made and
unmade by a reconcilable, with no intent of its own. A Pod in Kubernetes is the artifact; the
ReplicaSet, Job or DaemonSet that owns it is the reconcilable, and the rules for what happens when
a Pod dies live in the owner, which is why the same Pod shape serves owners with different rules.

```java
// kinotic-core: org.kinotic.core.api.reconcile
public interface Watched extends Identifiable<String> {
    WatchedState getState();
}
public interface Reconcilable<S> extends Watched {
    @Override ReconcileState<S> getState();
}

@Getter @Setter @Accessors(chain = true) @NoArgsConstructor
public class WatchedState {
    private List<StatusCondition> conditions = new ArrayList<>();   // one entry per type; each type names its one writer
    private WatchedParent parent;                       // what made this record and unmakes it; told when this changes
    private boolean dirty;                              // set by every write in the same shard operation; the master's trigger
    private long dirtyAt;                               // when, so the master's clear is compare-and-clear
}

@Getter @Setter @Accessors(chain = true) @NoArgsConstructor
public class ReconcileState<S> extends WatchedState {
    private S desired;                                  // intent, written only through updateDesired
    private S observed;                                 // the authority's last word, written only through reportObserved
    private long generation;                            // bumped by every desired write
    private long observedGeneration;                    // what the authority or worker had processed when it reported
    private Date deletionRequested;                     // deletion is intent; a worker finalizes
    private boolean reconciled;                         // stored, recomputed by every write, queried by the master and the console
}

public record StatusCondition(StatusConditionType type, String message, Date since) {}
public enum WatchedType { WORKLOAD, PROJECT_DEPLOYMENT, MICROSERVICE_DEPLOYMENT, UI_DEPLOYMENT, VM_NODE }   // a closed set, so an enum, where K8s needs a kind string
public record WatchedParent(WatchedType type, String id) {
    @JsonValue public String value() { return type + ":" + id; }   // stored as one keyword; the enum never holds a colon
}

// the comparable core: the same small record on both sides, reusing the enums that exist
public record DeploymentState(DeploymentStatusType phase, String commitSha) {}    // Project, Microservice and Ui deployments
public record VmNodeState(VmNodeStatusType phase) {}                              // VmNode
```

```java
// an artifact composes the watched contract; the node's word stays the entity's own field
public class Workload implements Watched {
    private WatchedState state = new WatchedState();    // NODE_UNREACHABLE set by the platform, cleared by the report; the parent
    private WorkloadStatus status;                      // written only from the node's report
    private Integer exitCode;                           // the same report
    private Date reportedAt;                            // the report's own clock, what a later report is ordered against
    private String image; private double cpus; private String nodeId; ...
}

// a reconcilable composes the same field with the extended type, so state.conditions, state.parent
// and state.dirty are one path on every index
public class MicroserviceDeployment implements Reconcilable<DeploymentState> {
    private ReconcileState<DeploymentState> state = new ReconcileState<>();
    private String workloadId; private String machineIdentityId; private String failureMessage; ...
}
```

- `desired` and `observed` are the same record, so converged is `desired.equals(observed)`; the
  transitional values (`DEPLOYING`, `PROVISIONING`) are observation-only and `updateDesired`
  refuses them with a guard, the way Puppet's `ensure` admits `running` or `stopped`.
- Ownership is per field and per condition type, not per struct: the report path writes a
  workload's `status` and `exitCode` and a node's `observed`, the reaper writes the
  `NODE_UNREACHABLE` entry and the authority's next report clears it. Each `StatusConditionType`
  documents its one writer. That is what "the most correct update wins" means mechanically: the
  writer who can know owns the field, and no write lands on another writer's field.
- `generation`/`observedGeneration` replaces every timestamp comparison on a reconcilable, and a
  gap is what "the authority has not seen the latest intent" looks like. An artifact has no intent
  to number, so its reports are ordered against each other on the node's own clock (`reportedAt`),
  never against a server-stamped `updated`, which is the comparison the partition trap was made of.
- Entities stay dumb. The convergence predicate exists once, in Painless, and is stored as
  `state.reconciled`; the master asks the index, not the record.
- `parent` is one pointer, up, because it expresses one relation, "made by", and that relation is a
  tree: a record is made by exactly one thing. Kubernetes carries several relations in its owner
  references and so needs a list and a `controller` flag; every other relation here is a field on
  the entity and a query by the worker that owns it, so the pointer stays one.
- A workload's run is its owner's: the owner creates it through `deployWorkload`, stops it
  through `stopWorkload`, and decides what its end means. `Reconciler<MicroserviceDeployment>`
  replaces a workload that ended; the deploy job records a failed sync as a failed deployment and
  never reruns it. No worker exists for a workload, and no restart policy lives on it.
- A workload's record is not its owner's to delete. It is the only link to the run's logs, which
  Loki holds under `{workload_id="..."}` in the organization's tenant, so it outlives the run and
  its owner, and is deleted only by an operator or by the retention sweep. Deleting it deletes those
  logs: `LokiClient.delete` posts `/loki/api/v1/delete` for the selector over the run's time
  range in the tenant, which Loki filters out of every query at once and removes from storage at
  its next compaction. Every path that deletes a workload record goes through that one operation.

### Writes: two repositories, scripts, one shard operation each

```java
// kinotic-domain: the only writer of state.conditions, state.parent and state.dirty on any index, and the ledger
public class WatchedStateRepository {
    Future<Boolean> setCondition(String indexName, String id, StatusCondition c);       // false when the type is present
    Future<Boolean> clearCondition(String indexName, String id, StatusConditionType t); // false when absent
    Future<Void> setParent(String indexName, String id, WatchedParent parent);
    <R> Future<Page<R>> findByParent(String indexName, Class<R> type, WatchedParent parent, Pageable p);   // WHERE state.parent = :parent
    <R> Future<Page<R>> findDirty(String indexName, Class<R> type, Pageable p);         // WHERE state.dirty = true
    Future<Void> clearDirty(String indexName, String id, long dirtyAt);                 // noop when a write landed since
    Future<Void> record(WatchEvent event);                                              // the ledger
}

// kinotic-domain: the reconcile half, composing the watched one for the ledger
public class ReconcileStateRepository {
    Future<Void> updateDesired(String indexName, String id, Object desired);            // noop when equal; else state.desired, generation += 1
    Future<Void> reportObserved(String indexName, String id, Object observed, long seen); // state.observed, observedGeneration
    Future<Void> requestDeletion(String indexName, String id);
    <R> Future<Page<R>> findUnreconciled(String indexName, Class<R> type, Pageable p);  // WHERE state.reconciled = false
}
```

```painless
// prefix on every state script, the ALLOCATION_FUNCTIONS idiom from VmNodeRepository
boolean reconciled(Map s) {
    return s.desired == s.observed
        && (long) s.generation == (long) s.observedGeneration
        && (s.conditions == null || s.conditions.isEmpty())
        && s.deletionRequested == null;
}
void touched(Map s, long now) {                       // every write ends here: the record is its own outbox
    s.dirty = true; s.dirtyAt = now;
    if (s.containsKey('reconciled')) { s.reconciled = reconciled(s); }   // only a reconcilable's state holds the flag
}
// SET_CONDITION, one script for watched and reconcilable alike
def s = ctx._source.state;
if (s.conditions.stream().anyMatch(c -> c.type == params.type)) { ctx.op = 'noop'; }
else { s.conditions.add(['type': params.type, 'message': params.message, 'since': params.since]); touched(s, params.now); }
// UPDATE_DESIRED: an identical write is a noop, so a fanned-out @Consumer writes once
if (s.desired == params.desired) { ctx.op = 'noop'; }
else { s.desired = params.desired; s.generation += 1; touched(s, params.now); }
// CLEAR_DIRTY: compare-and-clear, so a write that landed between the master's scan and its clear stays dirty
if ((long) s.dirtyAt != (long) params.dirtyAt) { ctx.op = 'noop'; } else { s.dirty = false; }
```

- Every script is the `Sync` kind (`refresh=wait_for`), so `findDirty` and `findUnreconciled` see a
  write on completion, as `reserveSync` does.
- Creation goes through the same scripts with `scripted_upsert`, so the flags are computed by the
  one function on insert and there is no Java copy of the predicate.
- Nothing else writes `state`. Any other write to a watched record is a partial update of the
  entity's own fields (`CrudServiceTemplate.partialUpdateSync`), never a whole-document save from a
  read copy; the workload's report path is one, it sets `state.dirty` in the same update, and it
  records `STATUS_REPORTED` in the ledger itself.
- `WorkloadRepository` extends `AbstractRepository`, `ProjectDeploymentRepository` extends
  `AbstractApplicationScopedRepository`, and the scoped bases are separate hierarchies, so the two
  repositories are composed, not inherited: each record's repository exposes the typed calls and
  delegates with its index name.
- Every script that changes the document records a `WatchEvent` (below) before completing.

### The master and the workers

```java
// one Ignite cluster singleton, the ServiceLivenessUpdater idiom
public class ReconcileMaster implements Service {
    @SpringResource(resourceClass = ReconcilerRegistry.class) private transient ReconcilerRegistry workers;   // Reconciler beans by WatchedType
    @SpringResource(resourceClass = WatchedRepositories.class) private transient WatchedRepositories repositories;   // every watched repository by WatchedType
    // init(): a tick timer; every record of every reconcilable type enqueued once, so Requeue.after timers restart when the master moves
    // tick, every watched index: findDirty -> for each: queue(record) when a worker exists for its type, queue(parent) when set, clearDirty(id, dirtyAt)
    // resync, every reconcilable index, on a longer period: findUnreconciled -> enqueue; the level-triggered backstop
    // execute(): drain the keyed queue: (type, id) -> attempts. A key in flight is not re-entered, a change
    //            during flight requeues it once, a failure backs off, Requeue.AFTER sets a timer
    private Future<Requeue> run(Key key) {                          // Key is the master's own (type, id), private to it
        return repositories.get(key.type()).findById(key.id())     // re-read; never act on the scan's copy
                .compose(current -> workers.get(key.type()).reconcile(current));
    }
}

// a Spring bean per type, in the master's JVM, the only thing that reads desired and observed
public interface Reconciler<R extends Reconcilable<?>> {
    Class<R> type();
    Future<Requeue> reconcile(R current);   // NONE, NOW, or AFTER(duration)
}
```

- The master's trigger is the dirty scan, not the event fabric. The Vert.x event bus does not
  guarantee delivery, and a lost notification for a change a parent must act on, a workload's run
  ending while its deployment looks converged, would strand that deployment until the next push.
  The flag lives in the document that changed, in the same shard operation, so it cannot be lost;
  a master that dies between queueing and clearing queues the key again; the scan is one term
  query per index, O(changes). A tick of a second or two is the whole latency. The fabric keeps
  carrying `@Emitter`/`@Consumer` events such as `GitHubProjectEvent`; nothing here reads or writes
  it, and `EventFabric.consume` is not needed.
- The GitHub path keeps its `@Consumer`, delivered to every server node as today, and fan-out stops
  mattering because the consumer no longer starts a job: it calls `updateDesired`, the second
  identical write is a noop, and the master reconciles the project once. Latest-wins comes for
  free: a push during a running job sets a newer `desired`, the key is requeued when the job's
  reconcile returns, and the worker sees only the newest commit.
- The master knows the envelope and nothing else. It never opens a state record; it compares none
  of their fields; it scans `state.dirty`, queries `state.reconciled`, and routes by `state.parent`.
- Workers run in the master's JVM, the Kubernetes controller-manager shape: one leader-elected
  process runs every controller, and the heavy work already runs elsewhere as grind jobs and
  workloads, so a worker is I/O. Publishing the workers over RPC would spread that I/O at the cost of
  serializing the record per call and a worker node dying mid-reconcile; nothing in `Reconciler`
  changes if that is ever wanted, so it is not designed in.
- `parent` is the pointer up and nothing else: who acts when this record changes, and whose
  deletion takes it along. Every other relation is a field on the entity and a query by the worker
  that owns the relation: a node's runs are `WHERE nodeId = :id`, found by `Reconciler<VmNode>`
  when the node falls silent, the way the node lifecycle controller lists pods by `spec.nodeName`.
  The N condition writes that fan out from one silent node set N flags, and the master's keyed
  queue coalesces them to one call per parent.
- A parent holds no list of its children. Each child index answers `findByParent`, so a parent
  with children of several types, `ProjectDeployment` with its microservice deployments, UI
  deployments and its sync and publish workloads, costs the parent nothing and its finalizer is one
  query per child type. A change two levels down reaches the top one level at a time: the child's
  flag queues its parent, the parent's worker writes its own `observed`, and that write's flag
  queues the grandparent. The tree is `ProjectDeployment` → `MicroserviceDeployment`/`UiDeployment`
  → `Workload`.
- The one time-based trigger is node liveness: `Reconciler<VmNode>` answers
  `Requeue.after(heartbeatTimeout)` for every node, so a silent node is found without a sweep, and
  the initial list in `init()` restarts those timers when the master moves. Dirty flags need no
  initial list: they persist.
- Deletion cascades down through `parent`, foreground: a parent's finalizer requests its children's
  deletion, requeues until they are gone, then deletes its own record. A project delete therefore
  writes `requestDeletion` on the `ProjectDeployment` and returns; the tree finalizes behind it, and
  the owner of a workload is what stops its VM: `stopWorkload`, and the record stays. The master
  collects orphans on resync: a record whose `parent` no longer exists gets `requestDeletion` when
  it is reconcilable and is left alone when it is an artifact, so a workload record is only ever
  deleted by an operator or by retention.

### The ledger: history and provenance

Conditions and `state` say what a record is now; the ledger says what happened to it, from where
and why, for a workload as much as for a deployment. The record stays the home of current state
because a GET is real-time and a data-stream search is refresh-delayed, and the ledger is not the
master's trigger: the dirty flag is.

```java
// kinotic-domain, recorded by WatchedStateRepository for every write to any watched record
public record WatchEvent(Date timestamp,
                         WatchedType type, String id,   // the record that changed
                         WatchedParent parent,          // what it belongs to
                         WatchEventKind kind,
                         String source,                 // the node, participant or server that caused it
                         String serverNodeId,           // where it was written
                         Long generation,               // null on an artifact
                         String message, Object value) {}
public enum WatchEventKind { CREATED, STATUS_REPORTED, CONDITION_SET, CONDITION_CLEARED, DESIRED_UPDATED, OBSERVED_REPORTED, DELETION_REQUESTED, DELETED }
```

```sql
CREATE DATA STREAM kinotic_watch_event (...) WITH (DATA_RETENTION = '30d', TIME_REFERENCE = 'timestamp') ;
```

### Wire and schema facts the phases are shaped by

- `ALTER TABLE ID ADD COLUMN ID type` (`KinoticSQL.g4:48`, `ID` has no dot) cannot add a sub-field to
  an `OBJECT` column later, so each record's `state` column is declared whole in the migration that
  gives it one, the watched shape for an artifact and the reconcile shape for a reconcilable, and no
  later phase touches the class, the field or the column.
- `conditions` is an `OBJECT` column, not `NESTED`: the only query on it is one sub-field
  (`state.conditions.type`), and the scripts read `_source`, which the mapping never touches.
  `CLAUDE.md` and the grammar reference now say when `NESTED` is correct.
- `parent` is one `KEYWORD`, `"MICROSERVICE_DEPLOYMENT:svc-a"`: `findByParent` is one term query,
  and Elasticsearch accepts a list wherever a single value was mapped, so the column never needs a
  migration whatever the pointer becomes.
- The workload report already carries the node's own timestamp; `reportedAt` on the record is what
  it is ordered against, so no vm-manager wire change is needed for the guard.

Names are decided: `Reconcilable` (not `Resource`, which is CRI's word), `desired`/`observed` (not
`spec`/`status`), `Watched`/`WatchedState` (the word `EventBusService.monitorClusterNodes` and
controller-runtime's `Watches()` share; not `Supervised`, which is `ServiceInvocationSupervisor`'s
Fowler sense, not `Tracked`, which says nothing), `ReconcileState`, `StatusCondition` (it qualifies
the status without being one), `WatchedParent parent` (not `owner`, which `JobOwner` uses for
tenancy; not `ref`, which says nothing), `WatchedType`, `WatchEvent` (not `RecordChanged`),
`ReconcileMaster`, `Reconciler`.

## Phases

| # | Phase | Files |
|---|---|---|
| 1 | **Node reports outrank server inference.** (PR #605, reworked.) `Watched`, `WatchedState`, `WatchedParent`, `WatchedType`, `StatusCondition`, `StatusConditionType`, `StatusConditions` (`find`/`has`) in `core/api/reconcile`; `Workload.state`; `WatchedStateRepository` in kinotic-domain with `setCondition`/`clearCondition` and the Painless functions `state`/`condition`/`reconciled`/`touched` every state script shares, composed by `WorkloadRepository`, which also gains a partial `updateRunSync(id, status, exitCode)` that sets `state.dirty`, so no path saves the whole record; the reaper sets `NODE_UNREACHABLE` and keeps the status and the room; a report from a conditioned workload is applied whatever its timestamp and clears it; an unanswered start or stop marks the workload instead of failing it; deregistering an OFFLINE node records its open runs FAILED; the microservice listing says so; V8 declares `state OBJECT (conditions OBJECT (type KEYWORD, message TEXT, since DATE), parent KEYWORD, dirty BOOLEAN, dirtyAt LONG)` whole. | core ×7, `Workload`, `WatchedStateRepository`, `WorkloadRepository`, `V8__workload_state.sql`, `DefaultVmNodeOrchestrationService`, `DefaultWorkloadOrchestrationService`, the two contracts, `DefaultMicroserviceDeploymentService` + contract, `WorkloadOrchestrationTest` + stub, docs |
| 2 | **The ledger.** `WatchEvent`, `WatchEventKind`, `WatchEventRepository` over `appendToDataStream`; `WatchedStateRepository.record`, called after each of Phase 1's two scripts and by the report path for `STATUS_REPORTED`, with `source` from the caller; V9 creates `kinotic_watch_event`; `findForRecord(type, id, page)` for the console. | domain ×3, `WatchedStateRepository`, the report path, V9, tests |
| 3 | **The same contract in the TypeScript packages.** `Watched`, `WatchedState`, `WatchedParent`, `WatchedType`, `StatusCondition`, `StatusConditionType` in `@kinotic-ai/core`, `Workload.state` in `@kinotic-ai/management-api`, version bumps and peer floors; the system console renders the condition once the packages are published. | core ×6 + package.json, management-api `Workload.ts` + package.json, peer floors, lock |
| 4 | **`Reconcilable` through `ProjectDeployment`.** `Reconcilable<S>`, `ReconcileState<S>`, `DeploymentState`; `ReconcileStateRepository` with `updateDesired` (noop when equal), `reportObserved`, `requestDeletion`, `findUnreconciled` and creation through `scripted_upsert`; `WatchedStateRepository` gains `setParent`, `findByParent`, `findDirty`, `clearDirty`; `ProjectDeployment.state` replaces `status` and `commitSha`, `failureMessage` keeps `DeploymentStatus.message`, with every caller (job factory, orchestrator, identity and artifact services, operations service, console listing); `DefaultProjectService.beforeDelete` writes `requestDeletion` on the deployment in place of deleting it; V10 declares `state` whole; TS model; docs. | sized by the reshaping |
| 5 | **The master.** `ReconcileMaster` as an Ignite cluster singleton, `ReconcilerRegistry`, `WatchedRepositories`, `Reconciler`, `Requeue`: the dirty scan with compare-and-clear, keyed queue, backoff, requeue-after, the initial list in `init()`, resync over `findUnreconciled`, parent routing, orphan collection. `ProjectDeployOrchestrator` becomes `Reconciler<ProjectDeployment>`: its `@Consumer` calls `updateDesired`, the worker runs the job and reports `observed`, and its finalizer requests deletion of the microservice and UI deployments, stops the sync and publish workloads, then deletes the machines and its own record once the children are gone; `deployingProjects`/`pendingDeploys` go. | master, registry, repositories, `Reconciler`, `Requeue`, orchestrator, its tests |
| 6 | **`VmNode` as a reconcilable, `Workload` as its owned artifact.** `Workload.reportedAt` (V11), creators set `state.parent`, the report path orders a report against `reportedAt` in place of the clock guard and sets `state.dirty` so the parent's worker runs on an ended run. `VmNode.state` with `VmNodeState`: `desired` `{ONLINE}` at registration, `observed` `{ONLINE}` or `{DRAINING}` from the heartbeat with `healthMessage` staying on the entity as `failureMessage` does on deployments, `OFFLINE` and `UNREACHABLE` leave `VmNodeStatusType` for the `NODE_UNREACHABLE` condition set by silence or an undeliverable call and cleared by the heartbeat; `findAvailableNode` selects `state.reconciled = true`; the JDK reaper becomes `Reconciler<VmNode>`, which marks a silent node and its open runs and requeues itself every heartbeat timeout; `deregisterNode` refuses a reachable node with open runs, otherwise writes `requestDeletion`, and the finalizer records the open runs FAILED, which routes each to its parent, and deletes the node; registration carries the node's inventory. | sized by the reshaping |
| 7 | **Workload records outlive their runs.** `destroyWorkload` removes the VM and leaves the record at the status the node reports for it; `deleteWorkload` is the one delete: it refuses an open run, deletes the run's log streams through `LokiClient.delete` in the organization's tenant, then the record; `DefaultDeploymentOperationsService.removeMicroservice` destroys and keeps; the retention sweep in the master deletes ended records older than `kinotic.systemApi.workload.retentionDays` through the same call; `values-loki-azure.yaml` gains the compactor retention and `delete_request_store` the delete API needs, as `values-loki.yaml` already has; `LokiClientIntegrationTest` covers the delete. | `LokiClient` + default, `WorkloadOrchestrationService` + default, operations service, properties, the sweep, helm values, test, docs |
| 8 | **`MicroserviceDeployment` as a reconciler.** `state` with `DeploymentState`; `ensureWorkload` becomes `Reconciler<MicroserviceDeployment>.reconcile`, run on a desired change, on its workload's flag through `parent`, and on resync; an open run marked `NODE_UNREACHABLE` counts as possibly running, so the worker waits for the node's report or the deregistration and never deploys beside it; a workload it replaces gets `stopWorkload`; console restart is `stopWorkload` plus a requeue, and the ended report routes back to redeploy; the finalizer stops its workload, then removes its machine and itself; `withRunState` goes. | sized by the reshaping |
| 9 | **`UiDeployment` as a reconciler.** `state` with `DeploymentState`; `provision`/`checkProvisioning` become the reconciler with `Requeue.after(30s)`; `schedulePoll`, `staleProvisioning` and `retryProvisioning` collapse into requeue and resync. | sized by the reshaping |
| 10 | **`JobRun` liveness.** A run's node leaving `monitorClusterNodes()` sets a condition on the run; Phase 8's stale-run rule reads it. | grind, small |

Dependency spine: 1 → 2 → 3; 4 → 5 → 6 → 7 → 8, 9; 10 after 5.

## Decisions

- **Workloads are artifacts, not reconcilables.** A workload has no intent of its own: it is made
  and unmade by the deployment that owns it, and what its end means is the owner's rule, replace for
  a microservice, record and stop for a sync. It carries the node's word, the platform's inference
  and its parent, and no `ReconcileState`, no worker, no restart policy. A job run is the same shape
  (Phase 10). This is the Pod: every controller in Kubernetes reads the same Pod status and each
  applies its own rule to it.
- **Two contracts, one extending the other.** Multiple writers and a ledger are one concern,
  `Watched`; intent and convergence are a second that presupposes the first, `Reconcilable`.
  `ReconcileState` extends `WatchedState` rather than composing it so that `state.conditions`,
  `state.parent` and `state.dirty` are one path on every index and one script serves both; a
  workload composes the watched contract and nothing else.
- **The trigger is the record, not the fabric.** Every write sets `state.dirty` in the same shard
  operation, the master scans the flag and compare-and-clears it, and `findUnreconciled` on a
  longer period is the level-triggered backstop. The event bus does not guarantee delivery and a
  parent cannot see a child's change in its own `reconciled` flag, so a lost notification would
  strand a deployment; a flag in the changed document cannot be lost. `EventFabric.consume` leaves
  the plan; the fabric keeps its `@Emitter`/`@Consumer` role.
- **Each record's contract is declared whole in the phase that gives it one.** `WatchedState`
  ships complete in Phase 1 with the workload, `parent` unwritten until Phase 6 and `dirty` set
  from Phase 1 and read from Phase 5; `ReconcileState` ships complete in Phase 4 with
  `ProjectDeployment`; each `state` column is declared whole in its own migration. The grammar
  cannot grow an `OBJECT` column later, and the no-rewrite rule prefers a field declared once over
  one grown across phases. `WatchedStateRepository` has one consumer in Phase 1 for the same
  reason: its second arrives in Phase 4, and putting the scripts on `WorkloadRepository` first
  would mean moving them.
- **`StatusConditionType` has one value in Phase 1.** `NODE_UNREACHABLE` serves the workload and,
  from Phase 6, the node itself; the second value arrives with the runner in Phase 10. A node's own
  problems stay a message on the entity, as a deployment's failure does, so no node-owned type is
  needed yet.
- **Deletion is intent and cascades through `parent`.** A delete on any level of the tree writes
  `requestDeletion`, the worker at that level finalizes foreground, and the management plane never
  needs a call into the system plane to stop a VM: it writes the deployment record, and the owner's
  worker, which lives in the system plane, calls `stopWorkload`. Today's leftovers, VMs of deleted
  projects, are stopped by their owners' finalizers once the owners are reconcilable.
- **Workload records are deleted only by hand or by retention, and their logs go with them.** The
  record is the only link to the run's logs, so no owner, finalizer or replace path deletes one;
  `deleteWorkload` is the single delete, it takes the Loki streams with the record, and the
  retention sweep and the console both call it. The Loki delete API needs the compactor's
  `retention_enabled` and a `delete_request_store` on every deployment, and a delete is filtered
  from queries at once and removed from storage after `delete_request_cancel_period`, so the
  record and the logs disappear together from the operator's view. Traces and metrics have no
  per-workload delete in Tempo or Mimir and follow their own retention.
- **The state records reuse the existing enums.** `DeploymentState(DeploymentStatusType,
  commitSha)`, `VmNodeState(VmNodeStatusType)`: the field `status` retires into
  `state.observed.phase` on reconcilables; the enums do not move, so the wire values and the TS
  packages keep them. `Workload.status` stays, since a workload is not reconciled.
- **What only the entity knows stays on the entity.** `exitCode`, `image`, `nodeId`, `workloadId`,
  `machineIdentityId`, `failureMessage`, `healthMessage`: the master never reads them and the worker
  gets the whole record, so they have no business in the contract.
- **Migrations are appended.** A test server runs everything and has applied the migration files
  that exist, so a schema change is a new versioned file, as PR #588 did with V6/V7 and Phase 1 does
  with V8; `CLAUDE.md` records the rule. V6's `reservations NESTED` is the same mistake as the
  original V8, already applied, and stays under the retype rule.
- **The TypeScript mirror is its own phase.** The wire shape leads from Java; the packages bump and
  publish as a unit, and the console change waits on the publish, as the console's `UNREACHABLE`
  handling did in the node-failure series.
- **The master is built in Phase 5, not later.** With three deployment reconcilers plus the node,
  it is justified before it exists, and the no-rewrite rule says to decide it before the deploy
  orchestrator is made a singleton, not after.
- **The workload routes to its parent before `MicroserviceDeployment` reconciles.** The
  microservice worker is driven by its workload's dirty flag, which the report path sets from
  Phase 6.
- **No edge repair loop.** PR #588 removed restart-in-place: an ended run leaves nothing on the node
  and a restart is a fresh VM from `ProjectWorkloadFactory`. Repair is therefore the server-side
  `Reconciler<MicroserviceDeployment>` of Phase 8, and the vm-manager keeps its role: observe, report,
  clean up.

## Already closed on develop

Lost updates on the node record (partial and scripted updates), capacity counters (the reservation
ledger, rebuilt at registration), dead-node deregistration (`countRunningForNode`), the non-clustered
mode (#496), and calls that hung when a node died (the request liveness series).
