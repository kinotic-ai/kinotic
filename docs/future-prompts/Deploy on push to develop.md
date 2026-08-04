# Continuous deployment of Kinotic apps on push to `develop`

This is a phased implementation plan. Each phase compiles and passes tests on its own and is a
reasonable PR boundary. Current-state claims below were verified against `develop` at `2e7f08a`
("Rename IamUser to ParticipantIdentity (#375)", 2026-08) — re-verify with fresh inspection
before acting on any of them; files move, and several load-bearing facts here (the orchestrator's
orphan status, the un-`@Publish`ed repo-token service, the missing orchestrator module gate) are
expected to change as this plan executes.

**STOP AT EVERY PHASE BOUNDARY.** When a phase is complete (implemented, tested, committed,
pushed), report what was done and wait for Navid's explicit approval before starting the next
phase. Do not begin any work belonging to a later phase while waiting — no "preparatory"
refactors, no scaffolding.

## Goal

When a developer lands changes on the `develop` branch of their project's GitHub repository, the
platform automatically deploys them: the project's entity definitions and migrations are applied
to the Kinotic server, and the project's Bun microservices run (or restart with the new code) on
a Kinotic VM node — with no manual action beyond the push. We follow gitflow; `develop` is the
only deploying branch for now. Production promotion is out of scope and is already sketched as
Phase 9 of `Multi-environment architecture.md` — nothing here may preclude it, and where the two
plans touch the same seam (orchestrator wiring, workload/environment linkage) this plan defers
to that one's shape.

Every deployment run is visible in the Kinotic UI as a persistent task with discrete steps,
per-step status, timestamps, and live + historical container logs. The task record and its UI
are the deliberately reusable piece; the deploy pipeline is their first and only producer.

## Target flow

```
push to develop
  └─ GitHubWebhookHandler (exists) ─► DefaultGitHubWebhookEventService (exists)
       └─ evt://github/push/<orgId>/<projectId> on the event bus (exists, no consumer today)
            └─ NEW GithubPushDeploymentTrigger (kinotic-orchestrator):
               filter ref == refs/heads/develop, coalesce + serialize per project,
               create TaskExecution (PENDING), run DeploymentPipeline
                 ├─ Step "checkout-sync"   BUILD workload, pinned to the app's node via
                 │                         VmManagerProxy.startWorkload(nodeId, ...), shared
                 │                         hostPath volume: git fetch/reset to head of develop,
                 │                         bun install, kinotic sync, write marker file
                 ├─ Step "start-runtime"   first deploy only: RUNTIME workload on the same
                 │                         node + hostPath (later deploys: the runtime's poller
                 │                         sees the marker and restarts its children; no step)
                 └─ TaskExecution updated per step; each step records its workloadId
                      └─ UI: task list + detail per project, per-step log tail via
                         LogService.tail(step.workloadId)  (log pipe already complete)
```

Two containers, one shared folder, one node. The build workload is short-lived and holds the
credentials; the runtime workload is long-lived, holds no credentials, and never stops on a
redeploy — it restarts its child `bun` processes when the marker file changes.

## Design decisions

Owner decisions from Navid (settled, not open questions):

- **Gitflow; `develop` only for now.** Production releases handled later via the promotion work.
- **One runtime container per project runs all of that project's microservices** as `bun`
  processes on the checked-out source (no compile step for develop).
- **The build step does not build** — it checks out, installs deps, and syncs entity
  definitions/migrations to the server (`kinotic sync`).
- **Redeploys never stop the runtime container.** The build step updates the shared project
  folder; the running container detects the change and restarts its children. The cross-VM
  polling approach is the one validated in `kinotic-js/boxlite-test/` (virtio-fs shares data but
  not inotify events; chokidar `usePolling` works).
- **Task records are persistent** and the task model/UI should be reusable for later platform
  operations — without falling into Speculative Generality: design only against the deploy
  pipeline that exists today.
- **UI deployment is out of scope** for this work (it will eventually also happen in the build
  step).

Decisions made by this plan (flag to Navid if any looks wrong; each has an open-questions entry
where judgment was required):

- **The marker file is the deploy commit point, and the runtime polls only the marker** —
  `<project>/.kinotic/DEPLOY`, written (write-temp + `rename`, same directory, so a poll never
  observes a partial file) as the build script's last action after `kinotic sync` succeeds.
  This solves the ordering race Navid identified: a tree-watching poller redeploys the moment
  `git reset` starts writing files, before sync has run. Watching one file makes checkout churn
  invisible, guarantees "server knows the definitions before the code serves" by construction,
  and drops polling cost from stat-ing a tree to stat-ing one file (a ~1s interval is fine —
  `volume-poll-test.ts` measured ~4s end-to-end at 300ms on a whole tree). In-place checkout
  into the shared folder stays exactly as validated. A `releases/<sha>/` layout with the marker
  pointing at the active release would add atomic cutover + instant rollback; that is the
  documented upgrade path when rollback becomes a requirement (promotion work), not now.
- **Deploy the head of `develop` at execution time, not the pushed SHA.** Webhook deliveries
  are un-deduped, un-ordered, and acked before processing (verified below), so the pipeline
  must be idempotent. Deploying the branch head makes any replay or stale delivery converge on
  the same result; `TaskExecution.commitSha` records what was actually deployed. No
  delivery-dedup store is built.
- **Coalesce + serialize per project.** At most one running deploy per project; while one runs,
  further pushes collapse into a single pending re-run (latest wins — it deploys the new head
  anyway).
- **`develop` is a constant, not a property** (house Properties rule: same value in every
  Kinotic Cloud environment). It is a cross-module contract (provisioner in kinotic-github,
  trigger in kinotic-orchestrator), so it lives with the other cross-module constants in
  `DomainUtil`. The branch→environment mapping entity comes with promotion, designed against
  that second concrete case.
- **`TaskExecution` carries no `type` discriminator yet.** One producer exists; a one-value
  enum is on the smells list. `name` carries the human title ("Deploy develop"). The
  discriminator is added when the second producer exists to design against — free to do while
  `kinoticVersion` is `-SNAPSHOT`.
- **The pipeline is a plain sequential service, not Grind.** Grind would bring
  `Progress`/`Diagnostic` events and step composition, but it is 2020 code that has never run
  inside a deployable, has no persistence, and `Multi-environment architecture.md:646-648`
  already sanctions the fallback: "a plain service method executing the steps sequentially is
  acceptable — do not build a *third* flow mechanism." A two-step pipeline does not need an
  engine. Re-evaluate when promotion (a genuinely longer flow) lands.
- **Credentials are minted server-side and injected into the build workload's `environment`;
  nothing gets `@Publish`ed for it.** The GitHub clone token comes from
  `GitHubProjectRepoService.issueRepoToken` called in-process (the service stays
  un-`@Publish`ed — its `// @Publish TODO` comment survives this plan). The sync credential is
  a short-lived project-scoped participant token (mechanism = open question 1). The runtime
  workload receives no credentials.
- **`Workload` gains `projectId`.** The pipeline must find "this project's runtime workload"
  across restarts; today `Workload` has no project linkage. Stamped by the pipeline, queried by
  it, and available to Alloy labels later. Edit `kinotic_workload` in `V1__init.sql` in place
  (`-SNAPSHOT` rule).

---

## Current state (verified anchors)

**The webhook → event-bus hook exists; nothing consumes it.**
`GitHubWebhookHandler` (`kinotic-github/.../internal/api/rest/GitHubWebhookHandler.java`)
mounts `POST /api/github/webhook` via `SuppliesGatewayRoutes`, HMAC-SHA256-verifies over raw
bytes (constant-time compare), and **acks 204 before processing**. Dispatch handles
`installation` / `installation_repositories` explicitly; everything else — including `push` —
falls to the `default` branch:

```java
// kinotic-github/.../internal/api/services/DefaultGitHubWebhookEventService.java:133-147
return projectRepository.findByRepoFullName(event.getRepoFullName())
        .thenAccept(projects -> {
            for (Project project : projects) {
                String cri = EventConstants.EVENT_DESTINATION_SCHEME + "://" + EVT_NAMESPACE + "/"
                        + event.getEventType()
                        + "/" + project.getOrganizationId() + "/" + project.getId();
                byte[] payload = event.getPayload().encode().getBytes(StandardCharsets.UTF_8);
                eventBusService.send(Event.create(cri, payload));   // evt://github/push/<org>/<project>
            }
        });
```

The CRI grammar is documented at `EventConstants.java:100-105`. No subscriber exists anywhere
(grep `evt://` / `EVENT_DESTINATION_SCHEME`). `GitHubWebhookEvent.deliveryId` is captured but
unused; the service Javadoc states there is no platform-side dedup and consumers must be
idempotent. `ref` / `head_commit` are never read — the full `JsonObject` payload rides along.
Server-side subscription exists as `EventBusService.listen(CRI)` returning an `EventConsumer`
(Vert.x-style `handler`/`exceptionHandler`) — `kinotic-core/.../EventBusService.java:41` — but
its routing granularity for `evt://` addresses (exact path vs `CRI#baseResource()`) must be
verified before Phase 3 commits to a subscription shape.

**Repo ↔ project association is a string lookup, and fans out.**
`ProjectRepository.findByRepoFullName` (`kinotic-domain/.../ProjectRepository.java:20-24`)
term-queries `repoFullName` and returns a *list* — the same repo can back projects in more than
one org; dispatch sends one event per project. `Project.repoDefaultBranch` is a provision-time
snapshot, never refreshed. The provisioner (`GitHubProjectRepoProvisioner`) pushes a single root
commit on the default branch only — **a freshly provisioned repo has no `develop` branch**.

**The repo-token surface exists, deliberately unexposed.**

```java
// kinotic-github/.../api/services/GitHubProjectRepoService.java:12-27
// @Publish TODO: not exposed until we are ready to use by worker nodes and security has been finalized
public interface GitHubProjectRepoService {
    CompletableFuture<GitHubRepoToken> issueRepoToken(String organizationId, String projectId);
    CompletableFuture<Void> createTag(String organizationId, String projectId, String tagName, String sha);
    CompletableFuture<Void> createBranch(String organizationId, String projectId, String branchName, String sha);
}
```

`issueRepoToken` already returns the clone URL + short-lived installation token
(`DefaultGitHubProjectRepoService.java:30-41`). `createBranch` is exactly what Phase 3 needs for
provisioning `develop`.

**Workload placement: one lever; hostPath is the share key.**

```java
// kinotic-orchestrator/.../internal/api/workload/DefaultWorkloadOrchestrationService.java:34-59
return nodeOrchestrationService.findAvailableNode(workload.getVcpus(), workload.getMemoryMb(), workload.getDiskSizeMb())
        .thenCompose(node -> {
            ...
            workload.setNodeId(node.getId());     // caller-supplied nodeId is always overwritten
            ...
            vmManagerProxy.startWorkload(node.getId(), savedWorkload)
```

```java
// kinotic-orchestrator/.../api/workload/VmManagerProxy.java:18-27 — the same-node primitive
@Proxy(namespace = "kinotic-ai.vm-manager", name = "VmManager")
public interface VmManagerProxy {
    CompletableFuture<Workload> startWorkload(@Scope String nodeId, Workload workload);
```

The `@Scope` param routes to the `DefaultVmManager` registered with that node id
(`vm-manager/src/internal/api/DefaultVmManager.ts:14-27`, `@Publish('kinotic-ai.vm-manager')`
with `@Scope get scope() { return this.nodeId }`). Two workloads share a folder by declaring the
same `hostPath` in `Workload.volumeMounts` (`VolumeMount`: hostPath/guestPath/readOnly);
`buildBoxOptions` (`BoxliteProvider.ts:41-80`) passes them through as virtio-fs mounts. There is
no affinity/named-volume concept; the hostPath string is the join key. No component creates a
volume-mount hostPath today — only the per-workload log dir is `mkdirSync`'d
(`BoxliteProvider.ts:138-139`).

**boxlite behavior (all proven in `kinotic-js/boxlite-test/`; see its README findings table):**

- virtio-fs **data** is coherent across boxes on the same node; **inotify is not** —
  `bun --watch` never fires on a cross-box edit (`volume-share-test.ts`).
- chokidar `{ usePolling: true, interval: 300 }` detects cross-box edits including nested files,
  ~4s end-to-end, and can drive kill-and-respawn reload (`volume-poll-test.ts:24-39` is the
  runner; chokidar was installed at runtime there — production bakes it into the image).
- **Entrypoint stdout/stderr is not host-visible.** Workloads must write log files under the
  always-mounted `GUEST_LOG_DIR = '/var/log/kinotic'` (`BoxliteProvider.ts:8-14`); that host dir
  is what Alloy ships to Loki.
- `stop()` is a pause; restarting a reused box re-runs the recorded entrypoint and silently
  ignores new options. Run-to-completion entrypoints zombie the box
  (`batch-workload-test.ts` / README finding 6) — a build job cannot rely on "box exits cleanly,
  state shows stopped." This forces an explicit completion signal (Phase 4, work item 5).

None of the polling/hot-reload machinery exists in production `vm-manager` code — grep
`chokidar|usePolling|watchFile` hits only `boxlite-test/`.

**`kinotic sync` uploads entity definitions, not source; human-authenticated; no completion
event.** The CLI (`kinotic-js/kinotic-cli/src/commands/synchronize.ts:44-135`) pushes C3 schemas
+ named queries per entity and applies `V<n>__*.sql` project migrations
(`ProjectMigrationService`). Auth is the OAuth device grant (`CliAuthenticator.ts:30-36`) —
interactive only. Completion is a console log (`synchronize.ts:118`); server-side, the only
events fired are per-entity `CacheEvictionEvent`s (cache-invalidation semantics, one per
save/publish, no first/last marker). The template README's claim that the server reads entity
definitions from GitHub is aspirational — no server code reads `.config/c3/**` (known doc
defect; fixed by the phase that ships the nearest behavior).

**`kinotic-orchestrator` is an orphan with no module gate.** No module depends on it
(`kinotic-server/build.gradle` lists core, domain, os-api, persistence, github, api-gateway),
and unlike every other library module, `KinoticOrchestratorLibrary` has **no**
`@ConditionalOnProperty` gate:

```java
// kinotic-orchestrator/src/main/java/org/kinotic/orchestrator/KinoticOrchestratorLibrary.java
@Configuration
@ComponentScan
@EnableConfigurationProperties
@EnableKinotic // enables proxy scanning and registration
public class KinoticOrchestratorLibrary {   // ← no kinotic.disable* gate, unlike every sibling
```

Its workload API is `@Zone(DomainUtil.SYSTEM_ZONE)` via `api/workload/package-info.java`. The
Grind engine (`api/grind/`) is in-memory only: no job id, no record, no persistence, not
`@Publish`ed; job history exists only while a `Flux` subscriber is attached.

**Task-record persistence has an exact precedent.** Platform records are
`AbstractRepository<T extends Identifiable<String>>` subclasses over `kinotic_*` indices
declared in `V1__init.sql`:

```java
// kinotic-domain/.../internal/api/repositories/WorkloadRepository.java:11-25 — the shape to copy
@Component
public class WorkloadRepository extends AbstractRepository<Workload> {
    public WorkloadRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_workload", Workload.class, crudServiceTemplate);
    }
```

Scoped variants exist (`AbstractOrganizationScopedRepository`, `AbstractApplicationScoped…`,
`AbstractProjectScoped…`). The `.sql` files are the kinotic-SQL DSL compiled to ES — there is no
relational database; `JSON NOT INDEXED` is the column type for opaque payloads.

**The log pipe is complete end-to-end; nothing consumes it.** Guest `*.log` under
`/var/log/kinotic` → per-node Grafana Alloy (`AlloyManager.ts` — config regenerated per workload
change, SIGHUP hot-reload, `tenant` label → `X-Scope-OrgID`, labels `workload_id`, `vm_id`,
`node_id`, `application_id`) → multi-tenant Loki → authorized read path:

```java
// kinotic-os-api/.../api/services/LogService.java:15-33
@Publish
public interface LogService {
    Flux<Buffer> tail(String workloadId);              // raw Loki tail frames — caller parses
    CompletableFuture<Buffer> history(LogQuery query); // raw query_range response
}
```

Authorization derives tenant + LogQL from the persisted `Workload` record
(`DefaultLogService.java:33-97`). The generated TS proxy exists
(`os-api/src/api/services/ILogService.ts:27-41`, `invokeStream` → RxJS `Observable`) with **no
caller anywhere** — `kinotic-frontend` has zero task/job/progress/workload/log UI. The STOMP
`invokeStream` transport is proven (`core/src/api/ServiceRegistry.ts:181-221`). There is no
client-side Loki-frame parser.

**Template layout** (`kinotic-ai/kinotic-tpl-isomorphic-ts`, wired as
`kinotic.github.repoTemplate`): Bun workspace, microservices at
`packages/microservices/<name>/src/main.ts`, each an independent entry registered in
`bunup.config.ts`; there is no supervisor/discovery beyond the workspace glob. Services are
`@Publish`-decorated classes instantiated before `Kinotic.connect()`; zone prefix
`appZone(orgId, appId)` must be set first. The template ships no `migrations/` directory and no
`sync` script — reconcile in Phase 4.

---

## Phase 1 — wire `kinotic-orchestrator` into `kinotic-server`

This answers `Multi-environment architecture.md` open question 3 (it already assumes yes: its
Phases 6 and 9 both need the module wired in). Update that doc's open-questions list in the same
change.

1. `kinotic-server/build.gradle`: add `implementation project(':kinotic-orchestrator')`.
2. Give the library the sibling gate it is missing, same idiom and fail-open default as
   `KinoticGithubLibrary`:

```java
// KinoticOrchestratorLibrary.java  (target shape)
@Configuration
@ComponentScan
@EnableConfigurationProperties
@EnableKinotic
@ConditionalOnProperty(value = "kinotic.disableOrchestrator", havingValue = "false", matchIfMissing = true)
public class KinoticOrchestratorLibrary {
```

   Register it in the module's `AutoConfiguration.imports` the way kinotic-github does
   (`kinotic-github/src/main/resources/META-INF/spring/…imports` →
   `…github_autoconfig.KinoticGithubAutoConfiguration`) — copy that autoconfig arrangement,
   don't invent a new one. Set `KINOTIC_DISABLEORCHESTRATOR: "true"` in
   `compose.kinotic-e2e-test.yml` and `kinotic-test/src/test/resources/application-test.yml`
   alongside the existing `KINOTIC_DISABLEGITHUB` entries **only if** boot-with-orchestrator
   breaks those contexts — prefer leaving it enabled everywhere and fixing what breaks.
3. Expect wiring fallout: the module has never run inside a deployable. `src/testx/` tests
   prove nothing about Spring context assembly. Resolve dependency/bean conflicts as found.
4. Verify end-to-end against a real vm-manager: `WorkloadOrchestrationService.deployWorkload`
   reachable over the wire, node registration + heartbeat active. The e2e harness
   (`kinotic-js/e2e-tests`, `compose.kinotic-e2e-test.yml`) is the place; a deploy/stop/destroy
   round-trip against a boxlite node is the acceptance test (gate on the environment providing
   `/dev/kvm`, the way `BoxliteProvider.recovery.test.ts:12-24` does).
5. `Workload` gains `projectId` (nullable — platform workloads have none): model, TS mirror in
   `os-api/src/api/model/workload/Workload.ts`, `kinotic_workload` columns in `V1__init.sql`
   edited in place, and `WorkloadRepository.findByProjectId(...)` following
   `findAllForNode`'s shape.

## Phase 2 — `TaskExecution` domain model + OS API + live stream

New domain objects in `kinotic-domain/src/main/java/org/kinotic/domain/api/model/task/` (three
related files justify the subpackage — model, step, statuses; mirrors `model/workload/`):

```java
@Getter @Setter @Accessors(chain = true) @NoArgsConstructor
public class TaskExecution implements ProjectScoped<String> {
    private String id;                    // UUID
    private String organizationId;
    private String applicationId;
    private String projectId;
    private String name;                  // human title, e.g. "Deploy develop"
    private TaskExecutionStatus status;   // enum: PENDING, RUNNING, SUCCEEDED, FAILED — own file
    private String commitSha;             // the sha actually deployed (head of develop at run time)
    private List<TaskStep> steps;
    private Date created;
    private Date updated;
}
```

```java
@Getter @Setter @Accessors(chain = true) @NoArgsConstructor
public class TaskStep {                   // persisted as opaque JSON on the record
    private String name;                  // "checkout-sync", "start-runtime"
    private TaskStepStatus status;        // enum: PENDING, RUNNING, SUCCEEDED, FAILED, SKIPPED — own file
    private String workloadId;            // nullable; the join key to LogService.tail/history
    private String error;                 // failure detail, null unless FAILED
    private Date started;
    private Date finished;
}
```

No `type` discriminator (design decision above); `commitSha` is deploy-specific and top-level
because "what sha is live?" must be queryable — reshape freely when a second producer exists
(`-SNAPSHOT` rule).

- **Migration**: add to `V1__init.sql` in place, mirroring the `kinotic_workload` block's style:

```sql
-- Create the task execution table for tracking platform task runs (deployments)
CREATE TABLE IF NOT EXISTS kinotic_task_execution (
    id KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    projectId KEYWORD,
    name KEYWORD,
    status KEYWORD,
    commitSha KEYWORD,
    steps JSON NOT INDEXED,
    created DATE,
    updated DATE
);
```

- **Repository**: `TaskExecutionRepository extends AbstractProjectScopedRepository<TaskExecution>`
  (`kinotic-domain/.../internal/api/repositories/`, next to `WorkloadRepository`), plus
  `findByProjectId(String, Pageable)` sorted `created` desc.
- **Service surface**: follow the `Workload`/`LogService` precedent *exactly as it exists* —
  verify which module publishes the workload-adjacent services to the frontend (the TS mirrors
  for `WorkloadService`/`VmNodeService` live in `@kinotic-ai/os-api`) and put
  `TaskExecutionService` in the same place, `os-api` zone, org-participant reads authorized by
  the same participant-check pattern `DefaultLogService.authorizedWorkload` uses (scope check
  against the record's `organizationId`; `SystemParticipant` passes). Surface:

```java
@Publish
public interface TaskExecutionService extends IdentifiableCrudService<TaskExecution, String> {
    CompletableFuture<Page<TaskExecution>> findByProjectId(String projectId, Pageable pageable);
    Flux<TaskExecution> streamForProject(String projectId);   // snapshot of recent runs, then live updates
}
```

  Mutations are **not** published — the pipeline writes through the repository/domain service
  in-process. (`IdentifiableCrudService` exposes `save`/`create`; if publishing the interface
  would expose mutations to org participants, restrict in the impl the way other services
  gate by participant type — verify how `DefaultApplicationService` authorizes and copy it.)
- **Live updates**: `streamForProject` is backed by an in-process `Sinks.many().multicast()`
  the writer emits to on every record save. This is correct on a single-node os-server, which
  is the current deployment reality; **cluster fan-out is open question 7** — the existing
  precedent for cross-node signaling is `ClusterCacheEvictionService`'s Ignite broadcast
  (`kinotic-persistence/.../ClusterCacheEvictionService.java:82-98`), and the UI degrades
  gracefully anyway (re-entering the page re-reads via `findByProjectId`). Do not build the
  cluster path until the deployment shape needs it.
- **TS mirror**: `ITaskExecutionService` + models in `@kinotic-ai/os-api`
  (`workspace/packages/os-api/src/api/…`), registered in `OsApiPlugin` — same generated-proxy
  shape as `ILogService.ts` (`invokeStream` for the stream method).
- **Tests**: through the service interface against real ES (the Testcontainers arrangement the
  repo already uses), covering scoped reads (org A cannot read org B's tasks) and the
  snapshot+live semantics of the stream.

## Phase 3 — `develop` at provision time + the push trigger

**3a — provision the branch.** After the provisioner's root commit lands, create `develop` from
it: `GitHubProjectRepoProvisioner` calls its own module's `createBranch(orgId, projectId,
DomainUtil.DEVELOP_BRANCH, rootCommitSha)` (it already knows the root SHA it pushed). Add
`DEVELOP_BRANCH = "develop"` to `DomainUtil` next to the zone constants — it is a genuine
cross-module contract (github provisioner + orchestrator trigger). Existing projects without a
`develop` branch: the trigger simply never fires for them until the branch is pushed manually —
acceptable; do not build a backfill.

**3b — the trigger.** New in kinotic-orchestrator (`internal/`; nothing else consumes it):
`GithubPushDeploymentTrigger`, subscribing at startup via
`eventBusService.listen(...)` (`EventBusService.java:41`).

**Verify the routing granularity first**: the CRI is
`evt://github/push/<orgId>/<projectId>` and `EventBusService`'s listener bookkeeping keys on
`CRI#baseResource()` — determine whether one `listen` on the `github` base resource receives
every github event (then filter `eventType == "push"` in the handler) or whether listening is
exact-path (then per-project subscriptions are unmanageable and the fallback applies).
Fallback, decided now so there is no design drift later: promote `push` to an explicit case in
`DefaultGitHubWebhookEventService`'s dispatch that sends to a **single constant CRI**
(`evt://github/push`, no per-project fan-out) carrying
`{organizationId, projectId, ref, headSha}` as the payload, which the trigger listens on.
Either way the github module keeps zero knowledge of deployment — it reports what happened;
the orchestrator decides what to do. Pick one mechanism; do not ship both.

Handler logic:

```java
// GithubPushDeploymentTrigger (target shape) — dedup-free by design: deploys converge on branch head
void onPush(PushNotification push) {                       // ref, orgId, projectId (payload-extracted)
    if (!("refs/heads/" + DomainUtil.DEVELOP_BRANCH).equals(push.ref())) {
        return;                                            // guard: only develop deploys
    }
    ProjectDeployState state = states.computeIfAbsent(push.projectId(), k -> new ProjectDeployState());
    synchronized (state) {
        if (state.running) {
            state.pending = true;                          // coalesce: latest head wins on the re-run
        } else {
            state.running = true;
            startDeploy(push.organizationId(), push.projectId());   // creates TaskExecution, runs pipeline
        }
    }
}
// on pipeline completion: if (state.pending) { state.pending = false; startDeploy(...); } else { state.running = false; }
```

`startDeploy` creates the `TaskExecution` (status `PENDING`, both steps `PENDING`) **before**
invoking the pipeline, so the ack-before-process crash window leaves a visible `PENDING` record
instead of silence (stale-`PENDING` sweeping is open question 8; the coalescing map is
in-memory, so a server restart mid-deploy also leaves a `RUNNING` record behind — same
question). The head SHA is resolved at execution time from GitHub (via the existing
`DefaultGitHubApiClient`, a ref lookup — small addition to `GitHubProjectRepoService`'s
internals if needed), not trusted from the webhook payload.

**Tests** (through the trigger's public entry, real event bus where feasible): push to
non-develop ref → no record; duplicate delivery → one deploy; three rapid pushes → at most two
deploys (one running + one coalesced re-run) and final state reflects the last head; push
during a running deploy → re-run after completion.

## Phase 4 — pipeline + container contracts

The heart of the feature. New in kinotic-orchestrator `internal/`: `DeploymentPipeline`
(single consumer today — the trigger; keep it one class until a second flow exists).

**4a — placement + the shared project dir.** Convention: the project folder lives on the node
at `<projectsBaseDir>/<projectId>`, where `projectsBaseDir` is a new `VmManagerConfig` dir
sibling of `logsBaseDir`/`stateDir` (`vm-manager/src/api/VmManagerConfig.ts:41-47`) —
guest-visible **only** through explicit mounts, and deliberately *not* under `logsBaseDir`
(same tenant-isolation reasoning as the state/logs split, `BoxliteProvider.ts:95-98`). The
vm-manager `mkdirSync`s a workload's volume-mount hostPaths **only when they fall under
`projectsBaseDir`** (creating arbitrary host paths on demand is not a capability the server
should have). First deploy: node chosen once with the runtime workload's resource ask via the
existing `findAvailableNode`; the build workload then pins to it. Subsequent deploys:
`workloadRepository.findByProjectId(...)` → reuse its `nodeId`. Both cases dispatch the build
via `vmManagerProxy.startWorkload(nodeId, buildWorkload)` — `deployWorkload`'s public
"orchestrator picks the node" contract is not changed.

**4b — the build workload.** Target shape (constants for image/sizing per the Properties rule —
open question 6 if Navid wants them configurable):

```java
Workload build = new Workload()
        .setName("build-" + project.getId())
        .setProjectId(project.getId())
        .setOrganizationId(project.getOrganizationId())
        .setImage(BUILD_IMAGE)                 // oven/bun-based + git + @kinotic-ai/kinotic-cli baked in
        .setVcpus(1).setMemoryMb(1024)
        .setEnvironment(Map.of(
                "KINOTIC_REPO_URL",   repoToken.cloneUrlWithToken(),  // issueRepoToken, in-process
                "KINOTIC_DEPLOY_SHA", headSha,
                "KINOTIC_SERVER_URL", serverUrl,
                "KINOTIC_TOKEN",      participantToken,               // short-lived, project-scoped (OQ 1)
                "KINOTIC_TASK_ID",    taskExecution.getId()))
        .setVolumeMounts(List.of(new VolumeMount()
                .setHostPath(projectsBaseDir + "/" + project.getId())
                .setGuestPath("/project")));
```

Build image entrypoint script (baked into the image; everything redirected — entrypoint stdout
is host-invisible):

```bash
#!/bin/sh
{
  set -e
  cd /project
  if [ -d .git ]; then git fetch origin "$KINOTIC_DEPLOY_SHA"      # git reset leaves untracked
  else git clone "$KINOTIC_REPO_URL" . ; fi                        # .kinotic/ intact — never git clean
  git reset --hard "$KINOTIC_DEPLOY_SHA"
  bun install --frozen-lockfile
  kinotic sync --server "$KINOTIC_SERVER_URL"                      # token from KINOTIC_TOKEN (4d)
  mkdir -p .kinotic
  printf '{"sha":"%s"}' "$KINOTIC_DEPLOY_SHA" > .kinotic/DEPLOY.tmp
  mv .kinotic/DEPLOY.tmp .kinotic/DEPLOY                           # the commit point: rename is atomic
} > /var/log/kinotic/build.log 2>&1
```

**4c — completion signaling.** boxlite gives no reliable "entrypoint exited, box stopped"
state (zombie finding), and `IVmManager` has no exec. Two candidates — decide with Navid
(open question 2), leaning to the second:

```java
// Option A — state polling: only if a real-VM test proves box state reflects entrypoint exit
pollUntil(() -> vmManagerProxy.getWorkload(buildId), w -> w.getStatus() == STOPPED, TIMEOUT);
```

```java
// Option B — the build reports back: last line of the build script (it is already authenticated)
//   kinotic report-task --task "$KINOTIC_TASK_ID" --step checkout-sync --status SUCCEEDED
// → small CLI subcommand invoking the TaskExecutionService step-update RPC; pipeline awaits it
//   with a hard timeout → FAILED + destroy the build workload either way
```

Option B works regardless of boxlite semantics and needs no vm-manager change, at the cost of
letting build-side code (which runs `bun install` postinstall scripts — open question 5) report
its own step status; scope the token's write authority to *its own* task record's steps.
Whichever wins, the pipeline `destroyWorkload`s the build box afterward (destroy also removes
its log dir — logs live on in Loki).

**4d — CLI non-interactive auth.** `CliAuthenticator` gains a guard clause: when
`KINOTIC_TOKEN` is set, authenticate the STOMP connection with it and skip the device grant.
One env var, no new command surface beyond `report-task` (if option B). Version skew between
the image-baked CLI and the project's pinned `@kinotic-ai` versions: pin the CLI version in the
build image and bump the image alongside CLI releases; note it in the image's README.

**4e — the runtime workload + runner.** Started by the pipeline only on first deploy (step
`start-runtime`; later deploys mark it `SKIPPED`):

```java
Workload runtime = new Workload()
        .setName("app-" + project.getId())
        .setProjectId(project.getId())
        .setOrganizationId(project.getOrganizationId())
        .setApplicationId(project.getApplicationId())      // Alloy labels pick this up for Loki
        .setImage(APP_RUNTIME_IMAGE)                       // oven/bun + chokidar + runner baked in
        .setVcpus(…).setMemoryMb(…)                        // constants; OQ 6
        .setEnvironment(Map.of("KINOTIC_SERVER_URL", serverUrl))   // no credentials — user code runs here
        .setVolumeMounts(List.of(sameHostPath.setGuestPath("/project")));
```

Runner contract (`/opt/kinotic/runner.ts` in the image — kept **off** the shared volume, the
same reason `volume-poll-test.ts:24` keeps its runner in `/root`):

```ts
// Watches ONLY the marker: build-box checkout churn never triggers a reload, and the reload
// can only observe a tree that kinotic sync has already blessed.
const MARKER = '/project/.kinotic/DEPLOY'
watch(MARKER, { usePolling: true, interval: 1000, ignoreInitial: false })
    .on('all', () => reload())

async function reload() {
    await stopChildren()                                   // SIGTERM, grace period, then SIGKILL (OQ 4)
    await run('bun', ['install', '--frozen-lockfile'], { cwd: '/project' })   // lockfile may have changed
    for (const entry of await glob('/project/packages/microservices/*/src/main.ts')) {
        spawnService(entry)   // one bun process per service; stdout/stderr → /var/log/kinotic/<name>.log
    }
}
// the runner process itself never exits (zombie-box rule); a crashed child is respawned with backoff
```

`ignoreInitial: false` + marker-present covers runtime-box restart after node reboot (recovery
re-runs the recorded entrypoint — `BoxliteProvider.recover()` — and the runner cold-starts from
the last blessed marker). First boot before any marker exists: the runner idles until the first
build writes one — which is why `start-runtime` is sequenced *after* `checkout-sync` succeeds.

**4f — template + docs reconciliation.** The template gains a `migrations/` directory and
whatever `README.md.liquid` correction matches shipped behavior (its "Kinotic OS reads entity
definitions from the connected GitHub repository" step becomes true *in effect* only once this
phase ships — reword to describe push-to-develop deployment). Rewrite
`website/content/01.apps/07.deployment/01.workflow.md` (currently aspirational
feature-branch/Firecracker/staging content) to describe the real flow, and grep
`website/content` for `sync`, `develop`, `deploy` to reconcile every hit (house docs rule).

**Tests.**

- **Real-VM vm-manager test** (gated on `/dev/kvm` like `BoxliteProvider.recovery.test.ts`):
  two workloads sharing a `projectsBaseDir` hostPath; box B writes tree files then the marker;
  assert box A's runner reloads exactly once, after the marker write, running the new code —
  this promotes the `boxlite-test` harness finding into a production regression test, and is
  also where Option A of 4c gets verified or killed.
- **Pipeline integration test** (Testcontainers ES + the e2e compose vm-manager): trigger a
  deploy for a seeded project against a local git fixture (stub the GitHub token mint at the
  `GitHubApiClient` boundary — the pipeline, not GitHub, is under test); assert
  `TaskExecution` walks PENDING→RUNNING→SUCCEEDED with workloadIds recorded, entity definitions
  land, second deploy skips `start-runtime` and the runtime picks up a changed file.
- **Failure path**: sync fails → marker absent, task FAILED with the error, runtime untouched
  and still serving the old code (this asserts the ordering guarantee, open question 3's
  accepted behavior).

## Phase 5 — frontend: task UI + log tailing

First consumer of both the `TaskExecution` stream and the entire log pipe — expect to shake
out bugs in the latter (it has never had a reader).

- **Loki frame parser** in `@kinotic-ai/os-api` next to `ILogService.ts` (it is the wire-format
  contract of that service — a genuine cross-boundary home, not a grab bag): decode `tail`
  websocket frames (`{streams:[{stream:{…labels}, values:[[tsNs, line], …]}]}`) and
  `history`/`query_range` responses into one `LogLine {timestamp, labels, line}` shape.
- **Pages** (follow `src/pages/routes.ts` conventions and the existing project-scoped page
  patterns, e.g. `ProjectEntityDefinitionsPage`):
  - Task list per project — `findByProjectId` for the snapshot, `streamForProject` for live
    updates; columns: name, status, commitSha (short), started, duration.
  - Task detail — step list with status/timestamps/error; selecting a step with a `workloadId`
    opens the log view: `history` for backfill, `tail` subscription for follow, both through
    the parser. PrimeVue progress components are already themed
    (`src/theme/progressbar.ts` / `progressspinner.ts`) — use them, don't restyle.
- Unsubscribe on route leave (the `Observable`s hold STOMP subscriptions open).
- Docs: an "observing deployments" page in `website/content` covering the task UI and per-step
  logs, cross-linked from the rewritten deployment workflow page.

---

## Out of scope

- Deploying the project's UI (will join the build step later; nothing here may depend on it).
- Production releases, promotion, environments, rollback — `Multi-environment architecture.md`
  Phases 6/9 own that ground; the `releases/<sha>` marker layout is the documented rollback
  upgrade path when it arrives.
- Preview deployments for feature branches / pull requests.
- Grind adoption/refactor and any task-framework generality beyond the record + UI shipped here.

## Open questions for Navid (answer before/at the phase that needs them)

1. **Machine identity for `kinotic sync`** (Phase 4): is a server-minted short-lived
   project-scoped participant token injected as `KINOTIC_TOKEN` acceptable, and what mints it
   (the existing JWT issuance path? a new grant type?) — this is the same "security has been
   finalized" decision that has kept `GitHubProjectRepoService` un-`@Publish`ed.
2. **Build completion signal** (Phase 4c): state polling (needs boxlite to reflect entrypoint
   exit — the zombie finding says it likely doesn't) vs the build reporting its own step over
   RPC (works today; build-side code can then touch its own task record). Plan leans B.
3. **Partial-sync failure surface** (Phase 4): a failed build after some entity definitions
   synced leaves old code running against new definitions, task FAILED. Acceptable for develop,
   or does sync need transactional semantics first (it has none today — per-entity RPCs)?
4. **Runtime restart semantics** (Phase 4e): SIGTERM grace period length; is dropping in-flight
   requests acceptable on develop? (Plan assumes yes, short grace.)
5. **`bun install` runs project-controlled postinstall scripts** inside the build workload,
   which holds the repo token + sync token. Acceptable for develop (it is the user's own
   project and the tokens are scoped to it), or must install and sync be separated into
   different containers?
6. **Resource sizing** (Phase 4): fixed platform constants for build/runtime vcpus/memory, or
   per-project configuration from day one? (Plan: constants; per-project sizing is config
   nobody asked for yet.)
7. **`TaskExecution` live-stream fan-out** (Phase 2): single-node in-process sink is assumed.
   If the os-server runs multi-replica before this ships, the Ignite-broadcast idiom
   (`ClusterCacheEvictionService`) is the precedent — build it then, or accept refresh-to-see?
8. **Crash hygiene** (Phase 3): a server crash mid-deploy leaves a `PENDING`/`RUNNING` record
   and an empty in-memory coalescing map. Sweep stale records to `FAILED` on startup, or leave
   them and let the next push supersede? (Plan: startup sweep of `RUNNING` older than a
   timeout — but only with Navid's nod, it's a guess.)
9. **No `type` on `TaskExecution`** (Phase 2): confirmed omission per the one-value-enum rule,
   or does Navid want the discriminator now despite it?
10. **Project dir lifecycle on the node** (Phase 4a): `<projectsBaseDir>/<projectId>` is never
    cleaned up today — when a project is deleted, who removes the folder and destroys the
    runtime workload? (Probably a `DefaultProjectService.delete` hook → orchestrator; decide
    whether it's in scope here or a fast-follow.)

## Guardrails for the implementer

- **One phase per approval** (see the stop rule at the top). If a phase needs something from a
  later phase, stop and raise it — don't pull work forward.
- Re-verify every `path:line` anchor before editing; don't trust the plan over the tree.
- CLAUDE.md rules apply in full: Lombok, enums over string constants, `api/` vs `internal/`
  layout, one top-level type per file, no version literals in module `build.gradle`, docs
  synced in the same change, and the smells catalog — in particular: no task-framework
  abstractions beyond the record + UI specified here, no config beyond what a Kinotic Cloud
  dev/prod split actually needs (image names, branch name, routes are constants), no test-only
  seams.
- The boxlite findings in `kinotic-js/boxlite-test/README.md` are load-bearing constraints
  (no cross-VM inotify, invisible entrypoint stdout, zombie run-to-completion boxes, reused-box
  option immutability). Re-run that harness if boxlite is upgraded past `0.9.x` before Phase 4.
- `Multi-environment architecture.md` will eventually make workloads environment-scoped
  (its Phase 6) and give promotion a real flow engine decision (its Phase 9). Keep
  `DeploymentPipeline` small and boring so it can be re-plumbed then without a restructuring.
