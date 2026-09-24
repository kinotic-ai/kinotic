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
and modules only declared services. State becomes uniform the same way. There are two kinds of
record. A **reconcilable** has intent of its own and a worker that converges the world to it: the
three deployments and the node. An **artifact** is made and unmade by a reconcilable and has no
intent of its own: a workload, a job run. A Pod in Kubernetes is the artifact; the ReplicaSet, Job
or DaemonSet that owns it is the reconcilable, and the rules for what happens when a Pod dies live
in the owner, which is why the same Pod shape serves owners with different rules.

```java
// kinotic-core: org.kinotic.core.api.reconcile
public interface Reconcilable<S> extends Identifiable<String> {
    ReconcileState<S> getState();
}

@Getter @Setter @Accessors(chain = true) @NoArgsConstructor
public class ReconcileState<S> {
    private S desired;                                  // intent, written only through updateDesired
    private S observed;                                 // the authority's last word, written only through reportObserved
    private List<StatusCondition> conditions = new ArrayList<>();   // one entry per type; each type names its one writer
    private long generation;                            // bumped by every desired write
    private long observedGeneration;                    // what the authority or worker had processed when it reported
    private Date deletionRequested;                     // deletion is intent; a worker finalizes
    private RecordRef owner;                            // who acts when this record changes
    private boolean reconciled;                         // stored, recomputed by every write, queried by the master
}

public record StatusCondition(StatusConditionType type, String message, Date since) {}
public record RecordRef(String type, String id) {}

// the comparable core: the same small record on both sides, reusing the enums that exist
public record DeploymentState(DeploymentStatusType phase, String commitSha) {}    // Project, Microservice and Ui deployments
public record VmNodeState(VmNodeStatusType phase) {}                              // VmNode
```

```java
// an artifact: no intent, no state object; the node's word, the platform's inference, and its owner
public class Workload implements Identifiable<String> {
    private WorkloadStatus status;                      // written only from the node's report
    private Integer exitCode;                           // the same report
    private Date reportedAt;                            // the report's own clock, what a later report is ordered against
    private List<StatusCondition> conditions;           // NODE_UNREACHABLE, set by the platform, cleared by the report
    private RecordRef owner;                            // the deployment that made it, where its changes are routed
    private String image; private double cpus; private String nodeId; ...
}
```

- `desired` and `observed` are the same record, so converged is `desired.equals(observed)`; the
  transitional values (`DEPLOYING`, `PROVISIONING`) are observation-only and `updateDesired`
  refuses them with a guard, the way Puppet's `ensure` admits `running` or `stopped`.
- Ownership is per field and per condition type, not per struct: the report path writes a
  workload's `status` and `exitCode` and a node's `observed`, the reaper writes the
  `NODE_UNREACHABLE` entry and the authority's next report clears it. Each `StatusConditionType`
  documents its one writer.
- `generation`/`observedGeneration` replaces every timestamp comparison on a reconcilable, and a
  gap is what "the authority has not seen the latest intent" looks like. An artifact has no intent
  to number, so its reports are ordered against each other on the node's own clock (`reportedAt`),
  never against a server-stamped `updated`, which is the comparison the partition trap was made of.
- Entities stay dumb. The convergence predicate exists once, in Painless, and is stored as
  `state.reconciled`; the master asks the index, not the record.
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

### Writes: one repository, scripts, one shard operation each

```java
// kinotic-domain: the only writer of state.* on any index, composed by each reconcilable's repository
public class ReconcileStateRepository {
    Future<Void> updateDesired(String indexName, String id, Object desired);            // state.desired, generation += 1
    Future<Void> reportObserved(String indexName, String id, Object observed, long seen); // state.observed, observedGeneration
    Future<Void> setCondition(String indexName, String id, StatusCondition c);          // noop when the type is present
    Future<Void> clearCondition(String indexName, String id, StatusConditionType t);    // noop when absent
    Future<Void> requestDeletion(String indexName, String id);
    <R> Future<Page<R>> findUnreconciled(String indexName, Class<R> type, Pageable p);  // WHERE state.reconciled = false
}
```

```painless
// prefix on every script, the ALLOCATION_FUNCTIONS idiom from VmNodeRepository
boolean reconciled(Map s) {
    return s.desired == s.observed
        && (long) s.generation == (long) s.observedGeneration
        && (s.conditions == null || s.conditions.isEmpty())
        && s.deletionRequested == null;
}
// SET_CONDITION
def s = ctx._source.state;
if (s.conditions.stream().anyMatch(c -> c.type == params.type)) { ctx.op = 'noop'; }
else { s.conditions.add(['type': params.type, 'message': params.message, 'since': params.since]); s.reconciled = reconciled(s); }
```

- Every script is the `Sync` kind (`refresh=wait_for`), so `findUnreconciled` sees a write on
  completion, as `reserveSync` does.
- Creation goes through the same scripts with `scripted_upsert`, so the flag is computed by the one
  function on insert and there is no Java copy of the predicate.
- Nothing else writes `state`. Any other write to a reconcilable's record is a partial update of
  the entity's own fields (`CrudServiceTemplate.partialUpdateSync`), never a whole-document save from
  a read copy.
- `WorkloadRepository` extends `AbstractRepository`, `ProjectDeploymentRepository` extends
  `AbstractApplicationScopedRepository`, and the scoped bases are separate hierarchies, so the
  repository is composed, not inherited: each reconcilable's repository exposes the typed calls and
  delegates with its index name.
- Every script that changes the document appends a `ReconcileEvent` (below) before completing.

### The master and the workers

```java
// one Ignite cluster singleton, the ServiceLivenessUpdater idiom
public class ReconcileMaster implements Service {
    @SpringResource(resourceClass = EventFabric.class) private transient EventFabric eventFabric;
    @SpringResource(resourceClass = ReconcilerRegistry.class) private transient ReconcilerRegistry workers;
    // init(): eventFabric.consume(RecordChanged.class, this::enqueue); resync timer
    // resync: for each registered repository, findUnreconciled(page) -> enqueue
    // enqueue(event): the record's key, and its owner's key when it has one
    // execute(): drain the keyed queue: (type, id) -> attempts. A key in flight is not re-entered, a change
    //            during flight requeues it once, a failure backs off, Requeue.AFTER sets a timer
    private Future<Requeue> run(ReconcileKey key) {
        return repositories.get(key.type()).findById(key.id())     // re-read; never act on the event's copy
                .compose(current -> workers.get(key.type()).reconcile(current));
    }
}

// a Spring bean per type, in the master's JVM, the only thing that reads desired and observed
public interface Reconciler<R extends Reconcilable<?>> {
    Class<R> type();
    Future<Requeue> reconcile(R current);   // NONE, NOW, or AFTER(duration)
}
```

- The master knows the envelope and nothing else. It never opens a state record; it compares none
  of their fields; it queries `state.reconciled` and routes by `owner`.
- Workers run in the master's JVM, the Kubernetes controller-manager shape: one leader-elected
  process runs every controller, and the heavy work already runs elsewhere as grind jobs and
  workloads, so a worker is I/O. Publishing the workers over RPC would spread that I/O at the cost of
  serializing the record per call and a worker node dying mid-reconcile; nothing in `Reconciler`
  changes if that is ever wanted, so it is not designed in.
- `owner` is the pointer up. Today `MicroserviceDeployment.workloadId` points down only; the event
  that must reach the microservice's worker is the workload's own report ending its run, so the child
  names its parent. Every write emits `RecordChanged(ref, owner, kind)` on the fabric; the master
  queues `ref` when a worker is registered for its type and `owner` whenever it is set, so an
  artifact's change reaches the worker that owns it and nothing else. The tree is
  `ProjectDeployment` → `MicroserviceDeployment`/`UiDeployment` → `Workload`.
- `init()` enqueues every record of every registered type once, the way an informer lists on start;
  resync enqueues `findUnreconciled`. The one time-based trigger is node liveness: `Reconciler<VmNode>`
  answers `Requeue.after(heartbeatTimeout)` for every node, so a silent node is found without a
  sweep, and the initial list restarts those timers when the master moves.
- Deletion cascades down through `owner`, foreground: a parent's finalizer requests its children's
  deletion, requeues until they are gone, then deletes its own record. A project delete therefore
  writes `requestDeletion` on the `ProjectDeployment` and returns; the tree finalizes behind it, and
  the owner of a workload is what stops its VM: `stopWorkload`, and the record stays. The master
  collects orphans on resync: a record whose `owner` no longer exists gets `requestDeletion` when
  it is reconcilable and is left alone when it is an artifact, so a workload record is only ever
  deleted by an operator or by retention.

### The event stream: history and provenance

Conditions and `state` say what a record is now; the stream says what happened to it, from where and
why. The record stays the home of current state because a GET is real-time and a data-stream search
is refresh-delayed.

```java
// kinotic-domain, appended by ReconcileStateRepository after every script that changed the document
public record ReconcileEvent(Date timestamp, RecordRef ref, ReconcileEventKind kind,
                             String source,          // the node, participant or server that caused it
                             String serverNodeId,    // where it was written
                             long generation, String message, Object value) {}
public enum ReconcileEventKind { DESIRED_UPDATED, OBSERVED_REPORTED, CONDITION_SET, CONDITION_CLEARED, DELETION_REQUESTED }
```

```sql
CREATE DATA STREAM kinotic_reconcile_event (...) WITH (DATA_RETENTION = '30d', TIME_REFERENCE = 'timestamp') ;
```

### Wire and schema facts the phases are shaped by

- `ALTER TABLE ID ADD COLUMN ID type` (`KinoticSQL.g4:48`, `ID` has no dot) cannot add a sub-field to
  an `OBJECT` column later, so each reconcilable's `state` column is declared whole in the migration
  that gives it one, and no later phase touches the class, the field or the column.
- `conditions` is an `OBJECT` column, not `NESTED`: the only query on it is one sub-field
  (`state.conditions.type`), and the scripts read `_source`, which the mapping never touches.
  `CLAUDE.md` and the grammar reference now say when `NESTED` is correct.
- The workload report already carries the node's own timestamp; `reportedAt` on the record is what
  it is ordered against, so no vm-manager wire change is needed for the guard.

Names are decided: `Reconcilable` (not `Resource`, which is CRI's word), `desired`/`observed` (not
`spec`/`status`), `ReconcileState`, `StatusCondition` (it qualifies the status without being one),
`RecordRef`, `ReconcileMaster`, `Reconciler`.

## Phases

| # | Phase | Files |
|---|---|---|
| 1 | **Node reports outrank server inference.** (PR #605, reworked.) `StatusCondition`, `StatusConditionType`, `StatusConditions` (Java `find`/`has` and the Painless condition functions) in `core/api/reconcile`; `Workload.conditions`; `WorkloadRepository` gains the `setCondition`/`clearCondition` scripts and a partial `updateRunSync(id, status, exitCode)` so no path saves the whole record; the reaper sets `NODE_UNREACHABLE` and keeps the status and the room; a report from a conditioned workload is applied whatever its timestamp and clears it; an unanswered start or stop marks the workload instead of failing it; deregistering an OFFLINE node records its open runs FAILED; the microservice listing says so; V8 adds `conditions OBJECT (...)`. | core ×3, `Workload`, `WorkloadRepository`, `V8__workload_conditions.sql`, `DefaultVmNodeOrchestrationService`, `DefaultWorkloadOrchestrationService`, the two contracts, `DefaultMicroserviceDeploymentService` + contract, `WorkloadOrchestrationTest` + stub, docs |
| 2 | **The event stream.** `ReconcileEvent`, `ReconcileEventKind`, `ReconcileEventRepository` over `appendToDataStream`; `ReconcileStateRepository` appends after each of Phase 1's two scripts, with `source` from the caller; V9 creates the data stream; `findForReconcilable(ref, page)` for the console. | domain ×3, `ReconcileStateRepository`, V9, tests |
| 3 | **The same contract in the TypeScript packages.** `StatusCondition`, `StatusConditionType` in `@kinotic-ai/core`, `Workload.conditions` in `@kinotic-ai/management-api`, version bumps and peer floors; the system console renders the condition once the packages are published. | core ×2 + package.json, management-api `Workload.ts` + package.json, peer floors, lock |
| 4 | **`EventFabric.consume`.** `EventFabric` becomes a `core/api/event` interface with `<T> Flux<T> consume(Class<T>)`; `DefaultEventFabric` keeps the downlink refcounting and `@Consumer` wiring becomes an adapter over `consume`. | `EventFabric` (api), `DefaultEventFabric`, `BeanWiring`, `EventFabricBeanPostProcessor`, tests |
| 5 | **`Reconcilable` through `ProjectDeployment`.** `Reconcilable<S>`, `ReconcileState<S>`, `RecordRef`, `DeploymentState`, the `RecordChanged` fabric event; `ReconcileStateRepository` with `updateDesired`, `reportObserved`, `setCondition`, `clearCondition`, `requestDeletion`, `findUnreconciled`, `reconciled()` and creation through `scripted_upsert`, its condition scripts sharing `StatusConditions`' Painless functions with `WorkloadRepository`; `ProjectDeployment.state` replaces `status` and `commitSha`, `failureMessage` keeps `DeploymentStatus.message`, with every caller (job factory, orchestrator, identity and artifact services, operations service, console listing); `DefaultProjectService.beforeDelete` writes `requestDeletion` on the deployment in place of deleting it; V10 declares `state` whole; TS model; docs. | sized by the reshaping |
| 6 | **The master.** `ReconcileMaster` as an Ignite cluster singleton, `ReconcilerRegistry`, `Reconciler`, `Requeue`: change subscription through `consume`, keyed queue, backoff, requeue-after, the initial list in `init()`, resync over `findUnreconciled`, owner routing, orphan collection. `ProjectDeployOrchestrator` becomes `Reconciler<ProjectDeployment>`: `@Consumer` calls `updateDesired`, the worker runs the job and reports `observed`, and its finalizer requests deletion of the microservice and UI deployments, stops the sync and publish workloads, then deletes the machines and its own record once the children are gone; `deployingProjects`/`pendingDeploys` go. | master, registry, `Reconciler`, `Requeue`, orchestrator, its tests |
| 7 | **`VmNode` as a reconcilable, `Workload` as its owned artifact.** `Workload.owner` and `reportedAt` (V11), creators set `owner`, the report path orders a report against `reportedAt` in place of the clock guard and emits `RecordChanged` so the owner's worker runs on an ended run. `VmNode.state` with `VmNodeState`: `desired` `{ONLINE}` at registration, `observed` `{ONLINE}` or `{DRAINING}` from the heartbeat with `healthMessage` staying on the entity as `failureMessage` does on deployments, `OFFLINE` and `UNREACHABLE` leave `VmNodeStatusType` for the `NODE_UNREACHABLE` condition set by silence or an undeliverable call and cleared by the heartbeat; `findAvailableNode` selects `state.reconciled = true`; the JDK reaper becomes `Reconciler<VmNode>`, which marks a silent node and its open runs and requeues itself every heartbeat timeout; `deregisterNode` refuses a reachable node with open runs, otherwise writes `requestDeletion`, and the finalizer records the open runs FAILED, which routes each to its owner, and deletes the node; registration carries the node's inventory. | sized by the reshaping |
| 8 | **Workload records outlive their runs.** `destroyWorkload` removes the VM and leaves the record at the status the node reports for it; `deleteWorkload` is the one delete: it refuses an open run, deletes the run's log streams through `LokiClient.delete` in the organization's tenant, then the record; `DefaultDeploymentOperationsService.removeMicroservice` destroys and keeps; the retention sweep in the master deletes ended records older than `kinotic.systemApi.workload.retentionDays` through the same call; `values-loki-azure.yaml` gains the compactor retention and `delete_request_store` the delete API needs, as `values-loki.yaml` already has; `LokiClientIntegrationTest` covers the delete. | `LokiClient` + default, `WorkloadOrchestrationService` + default, operations service, properties, the sweep, helm values, test, docs |
| 9 | **`MicroserviceDeployment` as a reconciler.** `state` with `DeploymentState`; `ensureWorkload` becomes `Reconciler<MicroserviceDeployment>.reconcile`, run on a desired change, on its workload's `RecordChanged` through `owner`, and on resync; a workload it replaces gets `stopWorkload`; console restart is `stopWorkload` plus a requeue, and the ended report routes back to redeploy; the finalizer stops its workload, then removes its machine and itself; `withRunState` goes. | sized by the reshaping |
| 10 | **`UiDeployment` as a reconciler.** `state` with `DeploymentState`; `provision`/`checkProvisioning` become the reconciler with `Requeue.after(30s)`; `schedulePoll`, `staleProvisioning` and `retryProvisioning` collapse into requeue and resync. | sized by the reshaping |
| 11 | **`JobRun` liveness.** A run's node leaving `monitorClusterNodes()` sets a condition on the run; Phase 9's stale-run rule reads it. | grind, small |

Dependency spine: 1 → 2 → 3; 4 → 6; 5 → 6 → 7 → 8 → 9, 10; 11 after 6.

## Decisions

- **Workloads are artifacts, not reconcilables.** A workload has no intent of its own: it is made
  and unmade by the deployment that owns it, and what its end means is the owner's rule, replace for
  a microservice, record and stop for a sync. It carries the node's word, the platform's inference
  and its owner, and no `state`, no worker, no restart policy. A job run is the same shape
  (Phase 11). This is the Pod: every controller in Kubernetes reads the same Pod status and each
  applies its own rule to it.
- **Each reconcilable's contract is declared whole in the phase that gives it one.**
  `ReconcileState` ships complete in Phase 5 with `ProjectDeployment`, and each `state` column is
  declared whole in its own migration; the grammar cannot grow an `OBJECT` column later, and the
  no-rewrite rule prefers a field declared once over one grown across phases.
- **`StatusConditionType` has one value in Phase 1.** `NODE_UNREACHABLE` serves the workload and,
  from Phase 7, the node itself; the second value arrives with the runner in Phase 11. A node's own
  problems stay a message on the entity, as a deployment's failure does, so no node-owned type is
  needed yet.
- **Deletion is intent and cascades through `owner`.** A delete on any level of the tree writes
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
- **The master is built in Phase 6, not later.** With three deployment reconcilers plus the node,
  it is justified before it exists, and the no-rewrite rule says to decide it before the deploy
  orchestrator is made a singleton, not after.
- **The workload routes to its owner before `MicroserviceDeployment` reconciles.** The microservice
  worker is driven by its workload's `RecordChanged`, which the report path emits from Phase 7.
- **No edge repair loop.** PR #588 removed restart-in-place: an ended run leaves nothing on the node
  and a restart is a fresh VM from `ProjectWorkloadFactory`. Repair is therefore the server-side
  `Reconciler<MicroserviceDeployment>` of Phase 9, and the vm-manager keeps its role: observe, report,
  clean up.

## Already closed on develop

Lost updates on the node record (partial and scripted updates), capacity counters (the reservation
ledger, rebuilt at registration), dead-node deregistration (`countRunningForNode`), the non-clustered
mode (#496), and calls that hung when a node died (the request liveness series).
