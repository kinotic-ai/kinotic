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

## The design

The RPC layer became uniform when the framework owned the shape (`@Publish`, `@Proxy`, CRI, `@Scope`)
and modules only declared services. State becomes uniform the same way: every reconcilable carries
one object holding the whole reconcile contract, and everything else stays on the entity.

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
    private ReconcilableRef owner;                      // who acts when this record changes
    private boolean reconciled;                         // stored, recomputed by every write, queried by the master
}

public record StatusCondition(StatusConditionType type, String message, Date since) {}
public record ReconcilableRef(String type, String id) {}

// the comparable core: the same small record on both sides, reusing the enums that exist
public record WorkloadState(WorkloadStatus phase) {}                              // Workload
public record DeploymentState(DeploymentStatusType phase, String commitSha) {}    // Project, Microservice and Ui deployments
public record VmNodeState(VmNodeStatusType phase) {}                              // VmNode
```

```java
// the entity keeps what only it knows, exactly where it lives today
public class Workload implements Reconcilable<WorkloadState> {
    private ReconcileState<WorkloadState> state = new ReconcileState<>();
    private Integer exitCode; private String image; private double cpus; private String nodeId; ...
}
```

- `desired` and `observed` are the same record, so converged is `desired.equals(observed)`; the
  transitional values (`STARTING`, `STOPPING`, `DEPLOYING`) are observation-only and `updateDesired`
  refuses them with a guard, the way Puppet's `ensure` admits `running` or `stopped`.
- Ownership is per field and per condition type, not per struct: the report path writes
  `observed` and `exitCode`, the reaper writes the `NODE_UNREACHABLE` entry and the node's report
  clears it, a node's own health report will write node-owned entries (Phase 7). Each
  `StatusConditionType` documents its one writer.
- `generation`/`observedGeneration` replaces every timestamp comparison, and a gap is what "the
  authority has not seen the latest intent" looks like.
- Entities stay dumb. The convergence predicate exists once, in Painless, and is stored as
  `state.reconciled`; the master asks the index, not the record.

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
    // init(): eventFabric.consume(ReconcilableChanged.class, this::enqueue); resync timer
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
  that must reach the microservice's worker is the workload's own report ending its run, and a failed
  workload is converged from its own view, so the child names its parent and the master queues both.
  The tree is `ProjectDeployment` → `MicroserviceDeployment`/`UiDeployment` → `Workload`.

### The event stream: history and provenance

Conditions and `state` say what a record is now; the stream says what happened to it, from where and
why. The record stays the home of current state because a GET is real-time and a data-stream search
is refresh-delayed.

```java
// kinotic-domain, appended by ReconcileStateRepository after every script that changed the document
public record ReconcileEvent(Date timestamp, ReconcilableRef ref, ReconcileEventKind kind,
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
  an `OBJECT` column later, so the `state` column is declared whole in the migration that first
  needs any part of it, and no later phase touches the class, the field or the column.
- `conditions` is an `OBJECT` column, not `NESTED`: the only query on it is one sub-field
  (`state.conditions.type`), and the scripts read `_source`, which the mapping never touches.
  `CLAUDE.md` and the grammar reference now say when `NESTED` is correct.
- The node's start call carries the workload's `generation` and its report carries the
  `observedGeneration` it acted on; that is the vm-manager wire change, and it replaces the clock
  guard.

Names are decided: `Reconcilable` (not `Resource`, which is CRI's word), `desired`/`observed` (not
`spec`/`status`), `ReconcileState`, `StatusCondition` (it qualifies the status without being one),
`ReconcileMaster`, `Reconciler`.

## Phases

| # | Phase | Files |
|---|---|---|
| 1 | **Node reports outrank server inference.** (PR #605, reworked.) `StatusCondition`, `StatusConditionType`, `StatusConditions` (`find`/`has`), `ReconcileState`, `ReconcilableRef` in `core/api/reconcile`; `WorkloadState`; `Workload.state`; `ReconcileStateRepository` with `SET_CONDITION`, `CLEAR_CONDITION` and `reconciled()`; `WorkloadRepository` composes it and gains a partial `updateRunSync(id, status, exitCode)` so no path saves the whole record; the reaper sets `NODE_UNREACHABLE` and keeps the status and the room; a report from a conditioned workload is applied whatever its timestamp and clears it; an unanswered start or stop marks the workload instead of failing it; deregistering an OFFLINE node records its open runs FAILED; the microservice listing says so; V8 declares `state` whole. | core ×5, `WorkloadState`, `Workload`, `ReconcileStateRepository`, `WorkloadRepository`, `V8__workload_state.sql`, `DefaultVmNodeOrchestrationService`, `DefaultWorkloadOrchestrationService`, the two contracts, `DefaultMicroserviceDeploymentService` + contract, `WorkloadOrchestrationTest` + stub, docs |
| 2 | **The event stream.** `ReconcileEvent`, `ReconcileEventKind`, `ReconcileEventRepository` over `appendToDataStream`; `ReconcileStateRepository` appends after each of Phase 1's two scripts, with `source` from the caller; V9 creates the data stream; `findForReconcilable(ref, page)` for the console. | domain ×3, `ReconcileStateRepository`, V9, tests |
| 3 | **The same contract in the TypeScript packages.** `StatusCondition`, `StatusConditionType`, `ReconcileState`, `ReconcilableRef` in `@kinotic-ai/core`, `WorkloadState` and `Workload.state` in `@kinotic-ai/management-api`, version bumps and peer floors; the system console renders the condition once the packages are published. | core ×4 + package.json, management-api ×2 + package.json, peer floors, lock |
| 4 | **`EventFabric.consume`.** `EventFabric` becomes a `core/api/event` interface with `<T> Flux<T> consume(Class<T>)`; `DefaultEventFabric` keeps the downlink refcounting and `@Consumer` wiring becomes an adapter over `consume`. | `EventFabric` (api), `DefaultEventFabric`, `BeanWiring`, `EventFabricBeanPostProcessor`, tests |
| 5 | **`Reconcilable` through `ProjectDeployment`.** `Reconcilable<S>`, `DeploymentState`, the `ReconcilableChanged` fabric event emitted by `ReconcileStateRepository`; the repository gains `updateDesired`, `reportObserved`, `requestDeletion`, `findUnreconciled` and creation through `scripted_upsert`; `ProjectDeployment.state` replaces `status` and `commitSha`, `failureMessage` keeps `DeploymentStatus.message`, with every caller (job factory, orchestrator, identity and artifact services, operations service, console listing), V10, TS model, docs. | sized by the reshaping |
| 6 | **The master.** `ReconcileMaster` as an Ignite cluster singleton, `ReconcilerRegistry`, `Reconciler`, `Requeue`: change subscription through `consume`, keyed queue, backoff, requeue-after, resync over `findUnreconciled`, owner routing. `ProjectDeployOrchestrator` becomes `Reconciler<ProjectDeployment>`: `@Consumer` calls `updateDesired`, the worker runs the job and reports `observed`; `deployingProjects`/`pendingDeploys` go. | master, registry, `Reconciler`, `Requeue`, orchestrator, its tests |
| 7 | **`Workload` and `VmNode` as reconcilables.** `Workload.status` becomes `state.observed.phase` through `reportObserved`, deploy and stop write `state.desired`, `generation` on the start call and `observedGeneration` on the report replace the clock guard, creators set `owner`; `VmNode.state` with `VmNodeState`, `UNREACHABLE` becomes a condition on the node, the node's health report writes node-owned conditions in place of `healthMessage`, the reaper becomes `Reconciler<VmNode>`, registration carries the node's inventory; vm-manager wire changes and publishes. | sized by the reshaping |
| 8 | **`MicroserviceDeployment` as a reconciler.** `state` with `DeploymentState`; `ensureWorkload` becomes `Reconciler<MicroserviceDeployment>.reconcile`, run on a desired change, on its workload's `ReconcilableChanged` through `owner`, and on resync; console restart is `requestDeletion` on the workload plus a requeue; `withRunState` goes. | sized by the reshaping |
| 9 | **`UiDeployment` as a reconciler.** `state` with `DeploymentState`; `provision`/`checkProvisioning` become the reconciler with `Requeue.after(30s)`; `schedulePoll`, `staleProvisioning` and `retryProvisioning` collapse into requeue and resync. | sized by the reshaping |
| 10 | **`JobRun` liveness.** A run's node leaving `monitorClusterNodes()` sets a condition on the run; Phase 8's stale-run rule reads it. | grind, small |

Dependency spine: 1 → 2 → 3; 4 → 6; 5 → 6 → 7 → 8, 9; 10 after 6.

## Decisions

- **The contract is declared whole in Phase 1 and filled phase by phase.** `ReconcileState` ships
  with every field and V8 with every sub-field, of which Phase 1 writes `conditions` and
  `reconciled`; the grammar cannot grow an `OBJECT` column later, and the no-rewrite rule prefers a
  field declared once over one grown in three phases.
- **`StatusConditionType` has one value in Phase 1.** Its second and third arrive with the node's
  health report in Phase 7 and the runner in Phase 10.
- **The state records reuse the existing enums.** `WorkloadState(WorkloadStatus)`,
  `DeploymentState(DeploymentStatusType, commitSha)`, `VmNodeState(VmNodeStatusType)`: the field
  `status` retires into `state.observed.phase`; the enums do not move, so the wire values and the TS
  packages keep them.
- **What only the entity knows stays on the entity.** `exitCode`, `image`, `nodeId`, `workloadId`,
  `machineIdentityId`, `failureMessage`: the master never reads them and the worker gets the whole
  record, so they have no business in the contract.
- **Migrations are appended.** A test server runs everything and has applied the migration files
  that exist, so a schema change is a new versioned file, as PR #588 did with V6/V7 and Phase 1 does
  with V8; `CLAUDE.md` records the rule. V6's `reservations NESTED` is the same mistake as the
  original V8, already applied, and stays under the retype rule.
- **The TypeScript mirror is its own phase.** The wire shape leads from Java; the packages bump and
  publish as a unit, and the console change waits on the publish, as the console's `UNREACHABLE`
  handling did in the node-failure series.
- **The master is built in Phase 6, not later.** With three deployment reconcilers plus the reaper,
  it is justified before it exists, and the no-rewrite rule says to decide it before the deploy
  orchestrator is made a singleton, not after.
- **`Workload` becomes reconcilable before `MicroserviceDeployment`.** The microservice worker is
  driven by its workload's `ReconcilableChanged`, which only exists once the workload's report goes
  through `reportObserved`.
- **No edge repair loop.** PR #588 removed restart-in-place: an ended run leaves nothing on the node
  and a restart is a fresh VM from `ProjectWorkloadFactory`. Repair is therefore the server-side
  `Reconciler<MicroserviceDeployment>` of Phase 8, and the vm-manager keeps its role: observe, report,
  clean up.

## Already closed on develop

Lost updates on the node record (partial and scripted updates), capacity counters (the reservation
ledger, rebuilt at registration), dead-node deregistration (`countRunningForNode`), the non-clustered
mode (#496), and calls that hung when a node died (the request liveness series).
