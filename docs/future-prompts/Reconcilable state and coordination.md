# Reconcilable state and coordination — phase plan

Plan of record for giving the platform one shape for stateful records and one way to converge them,
in place of the status field, sweep and conflict rule each record type carries today. Phase 1 is the
PR this document lands with. Everything below was validated against `develop` at `a298699`
(2026-09-23), after PR #588.

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

## The design

The RPC layer became uniform when the framework owned the shape (`@Publish`, `@Proxy`, CRI, `@Scope`)
and modules only declared services. State becomes uniform the same way:

```java
// kinotic-core: org.kinotic.core.api.reconcile
public interface Reconcilable<D, O> extends Identifiable<String> {
    D getDesired();                     // intent — callers, only through updateDesired
    O getObserved();                    // observation — only the record's authority
    List<Condition> getConditions();    // inference — only the platform
    long getGeneration();               // bumped by every desired write
    long getObservedGeneration();       // what the authority had seen when it reported
    Instant getDeletionRequested();     // deletion is intent; a reconciler finalizes
}
public record Condition(ConditionType type, String message, Date since) {}
public interface Reconciler<R extends Reconcilable<?, ?>> { Class<R> type(); Future<Requeue> reconcile(R current); }
```

- `generation`/`observedGeneration` replaces every timestamp comparison, and a gap is what "the
  authority has not seen the latest intent" looks like.
- `observed` is writable only by the record's authority, checked against the caller's participant
  the way `ProjectArtifactService.recordArtifacts` already checks the sync machine identity.
- Inference lives in `conditions` and can never reach `observed`.
- Writes go through `CrudServiceTemplate.partialUpdate` and `scriptedUpdateSync` (PR #558), so no
  compare-and-set plumbing is needed; every write emits a change event on the event fabric.
- Reconcilers run under one Ignite cluster singleton, the `ServiceLivenessUpdater` idiom, which
  subscribes to change events in `init()`, keeps a keyed work queue with backoff and requeue-after,
  resyncs periodically, and re-reads the record before every call.

Names are decided: `Reconcilable` (not `Resource`, which is CRI's word), `desired`/`observed` (not
`spec`/`status`, since `status` is the overloaded word being retired), `Reconciler`.

## Phases

| # | Phase | Files |
|---|---|---|
| 1 | **Node reports outrank server inference.** `Condition`, `ConditionType`, `Conditions` in `core/api/reconcile`; `Workload.conditions`; the reaper sets `NODE_UNREACHABLE` and keeps the status and the room; a report from a conditioned workload is applied whatever its timestamp and clears it; an unanswered start or stop marks the workload instead of failing it; deregistering an OFFLINE node records its open runs FAILED; the microservice listing says so. | core ×3, `Workload`, `V8__workload_conditions.sql`, `DefaultVmNodeOrchestrationService`, `DefaultWorkloadOrchestrationService`, the two contracts, `DefaultMicroserviceDeploymentService` + contract, `WorkloadOrchestrationTest` + stub, docs |
| 2 | **The same contract in the TypeScript packages.** `Condition`/`ConditionType` in `@kinotic-ai/core`, `Workload.conditions` in `@kinotic-ai/management-api`, version bumps and peer floors; the system console renders the condition once the packages are published. | core ×3 + package.json, management-api `Workload.ts` + package.json, peer floors, lock |
| 3 | **`EventFabric.consume`.** `EventFabric` becomes a `core/api/event` interface with `<T> Flux<T> consume(Class<T>)`; `DefaultEventFabric` keeps the downlink refcounting and `@Consumer` wiring becomes an adapter over `consume`. | `EventFabric` (api), `DefaultEventFabric`, `BeanWiring`, `EventFabricBeanPostProcessor`, tests |
| 4 | **`Reconcilable` through `ProjectDeployment`.** The interface, `ReconcilableCrudService` and `AbstractReconcilableRepository` in kinotic-domain, the change event; `ProjectDeployment` reshaped into `desired{commitSha}` / `observed{...}` with every caller (job factory, orchestrator, identity and artifact services, operations service, console listing), migration, TS model, docs. | sized by the reshaping |
| 5 | **The reconciler host.** One Ignite `Service` hosting the `Reconciler` beans: change subscription through `consume`, keyed queue, backoff, requeue-after, resync. `ProjectDeployOrchestrator` becomes `Reconciler<ProjectDeployment>`: `@Consumer` writes `desired.commitSha`, the reconciler runs the job, `init()` resyncs; `deployingProjects`/`pendingDeploys` go. | host, `Reconciler`, `Requeue`, orchestrator, its tests |
| 6 | **`MicroserviceDeployment` as a reconciler.** Reshaped to `desired{commitSha, entryPoint, present}` / `observed{workloadId, machineIdentityId, phase}`; `ensureWorkload` becomes `Reconciler<MicroserviceDeployment>.reconcile`, run on a desired change, on the workload's ended report and on resync; console restart is a stop plus a requeue; `withRunState` goes. | sized by the reshaping |
| 7 | **`UiDeployment` as a reconciler.** Reshaped to `desired{commitSha, present}` / `observed{servedCommit, servesHtml, phase}`; `provision`/`checkProvisioning` become the reconciler with `Requeue.after(30s)`; `schedulePoll`, `staleProvisioning` and `retryProvisioning` collapse into requeue and resync. | sized by the reshaping |
| 8 | **`Workload` and `VmNode` as reconcilables.** `observed.phase`, `generation` replaces the clock guard, `UNREACHABLE` becomes a condition on the node, the reaper becomes `Reconciler<VmNode>` on the host, registration carries the node's inventory; vm-manager wire changes and publishes. | sized by the reshaping |
| 9 | **`JobRun` liveness.** A run's node leaving `monitorClusterNodes()` sets a condition on the run; Phase 6's stale-run rule reads it. | grind, small |

Dependency spine: 1 → 2; 3 → 5; 4 → 5 → 6, 7; 4 → 8; 1 → 8; 9 after 5.

## Decisions

- **`ConditionType` has one value in Phase 1.** Its second and third arrive with the site deadline in
  Phase 7 and the runner in Phase 9; the enum exists now because the record's shape must not be
  reshaped later.
- **Migrations are appended.** PR #588 added V6/V7 rather than editing V1, because running
  environments had applied V1–V5. Phase 1 follows that precedent with V8. `CLAUDE.md`'s snapshot rule
  still says to edit V1 in place; one of the two is to be updated by the owner.
- **The TypeScript mirror is its own phase.** The wire shape leads from Java; the packages bump and
  publish as a unit, and the console change waits on the publish, as the console's `UNREACHABLE`
  handling did in the node-failure series.
- **The host is built in Phase 5, not later.** With three deployment reconcilers plus the reaper,
  the host is justified before it exists, and the no-rewrite rule says to decide it before the deploy
  orchestrator is made a singleton, not after.
- **No edge repair loop.** PR #588 removed restart-in-place: an ended run leaves nothing on the node
  and a restart is a fresh VM from `ProjectWorkloadFactory`. Repair is therefore the server-side
  `Reconciler<MicroserviceDeployment>` of Phase 6, and the vm-manager keeps its role: observe, report,
  clean up.

## Already closed on develop

Lost updates on the node record (partial and scripted updates), capacity counters (the reservation
ledger, rebuilt at registration), dead-node deregistration (`countRunningForNode`), the non-clustered
mode (#496), and calls that hung when a node died (the request liveness series).
