# Continuous deployment of Kinotic apps from GitHub (`develop` → development)

This is a phased implementation plan. Each phase compiles and passes tests on its own and is a
reasonable PR boundary. Current-state claims below were verified against `develop` at `2e7f08a`
("Rename IamUser to ParticipantIdentity", 2026-08) — re-verify with fresh inspection before
acting on any of them.

**STOP AT EVERY PHASE BOUNDARY.** When a phase is complete (implemented, tested, committed,
pushed), report what was done and wait for Navid's explicit approval before starting the next
phase. Do not begin any work belonging to a later phase while waiting.

## Goal

When a developer lands changes on the `develop` branch of a project's repository, the platform
deploys those changes automatically: entity definitions and pending migrations are applied to
the Kinotic server first, then the project's microservices run the new code. No manual action
beyond the push, no CI in the developer's repository. `develop` is the only deploying branch
and it deploys to the single (development) environment; promotion to other environments is the
Phase 9 Promotion flow of `docs/future-prompts/Multi-environment architecture.md` and is out of
scope here — but everything below is shaped so that flow can consume it later.

This plan makes an existing documented promise true. The docs already describe a server-side
pull model with no developer-side CI:

> `website/content/01.apps/02.quick-start.md:87` — "Kinotic OS reads entity definitions from
> the connected GitHub repository and synchronizes them for you -- publishing the entity
> creates the backing storage and registers its services. There is nothing to log in to or
> push from your machine besides git."

Nothing implements that today.

## Requirements (from the feature request)

1. **Trigger** — push to `develop` deploys that project; other branches do nothing; rapid
   pushes, duplicate deliveries, and out-of-order deliveries must converge on the latest state
   of `develop`.
2. **Server state before code** — entity definitions + pending migrations apply before the new
   code begins serving.
3. **Updates over teardowns** — redeploys update running services in place (a restart is fine).
4. **First deployment** — the first push bootstraps everything; no pre-provisioning.
5. **Observability** — each deployment is a persistent task with discrete steps, per-step
   status/timestamps, and live + historical per-step logs, queryable after restarts.
6. **Reusable task tracking** — the task record model and UI are generic; the machinery is
   built only against the one producer that exists (deployment). No plugin registries, retry
   policies, or DAG engines.
7. **Security** — deployment credentials are short-lived, scoped to the one project, and never
   exposed to or configurable by the developer's application code.

---

## Current state (verified anchors)

**The trigger seam already exists and dangles.** GitHub webhooks are received, verified, and
republished per-project on the cluster event bus — and nothing consumes them:

```java
// kinotic-github/.../internal/api/services/DefaultGitHubWebhookEventService.java:140-146
for (Project project : projects) {
    String cri = EventConstants.EVENT_DESTINATION_SCHEME + "://" + EVT_NAMESPACE + "/"
            + event.getEventType()
            + "/" + project.getOrganizationId() + "/" + project.getId();
    byte[] payload = event.getPayload().encode().getBytes(StandardCharsets.UTF_8);
    eventBusService.send(Event.create(cri, payload));
}
```

`EventConstants.java:103` documents the shape (`evt://github/push/<org>/<project>`). A repo-wide
grep finds no listener. Webhook deliveries carry no dedup (`GitHubWebhookEventService.java`
Javadoc says consumers must be idempotent).

**A latent bug blocks push payloads.** The gateway mounts a global 16 KiB body limit *before*
module routes, so the webhook handler's own 25 MiB limit never applies — a routine `push`
payload (> 16 KiB with a handful of commits) is rejected 413 before HMAC verification runs:

```java
// kinotic-api-gateway/.../internal/endpoints/ApiGatewayVertcleFactory.java:59  — mounted first
router.route("/api/*").handler(BodyHandler.create(false).setBodyLimit(16384));
```
```java
// kinotic-github/.../internal/api/rest/GitHubWebhookHandler.java:58  — never reached in time
.handler(BodyHandler.create().setBodyLimit(WEBHOOK_BODY_LIMIT_BYTES))   // 25 MiB
```

**The only implemented apply path is the CLI, and the template doesn't use it.**
`kinotic-js/kinotic-cli/src/commands/synchronize.ts:65-116` orchestrates the whole sequence —
create app/project if missing, sync named queries, then entities (`EntityDefinitionService`
`create`/`save`/`publish`), then migrations (`MigrationService.executeMigrations` on
`./migrations/V<n>__<desc>.sql`). The template (`kinotic-tpl-isomorphic-ts`) wires only
`kinotic generate` (local codegen), has **no `migrations/` directory, no `.github/` workflows**,
and its `main.ts` hardcodes `localhost:58503` with no credentials.

**Server-side services for definitions and migrations exist and are `@Publish`ed.**
`EntityDefinitionService` (`kinotic-persistence/.../api/services/EntityDefinitionService.java:16`)
creates/updates ES indices on `publish`/`save`; `MigrationService`
(`.../api/services/MigrationService.java:13`) executes project migrations through
`MigrationExecutor`, which tracks `(version, projectId)` in the `migration_history` index and
skips applied versions — idempotent by construction. Two open FIXMEs both trace to missing
serialization:

```java
// kinotic-sql/.../executor/MigrationExecutor.java:80-81
// FIXME: make sure migrations are not currently running for the same project
// Was thinking we can use Apache Ignite distributed lock or ACID cache transactions for this
```
```java
// kinotic-persistence/.../DefaultEntityDefinitionService.java:273-277 (abridged)
// FIXME: ... serious race conditions if multiple clients are updating the same EntityDefinition
```

**Code runs as micro-VM workloads, but nothing produces a deployable from a Project.**
`WorkloadOrchestrationService` (`kinotic-orchestrator/.../api/workload/`, `@Publish`, zone
`system`) offers `deployWorkload`/`restartWorkload`/`stopWorkload`/`destroyWorkload`, dispatched
over RPC to the Bun `vm-manager` agent (`kinotic-js/workspace/packages/vm-manager`) which boots
OCI images via boxlite. `Workload.image` is required; there is no image build pipeline and no
link from `Workload` to a `Project` or commit. `kinotic-orchestrator` is an orphan module — no
deployable depends on it — and it lacks the `kinotic.disable*` gate every sibling has.

**Short-lived repo credentials exist, unpublished.** The GitHub App integration mints
installation tokens scoped by `repository_ids` and permission set (Caffeine-cached, ~1 h TTL,
`DefaultGitHubApiClient.mintToken:276-298`), and a worker-facing service is written but held
back:

```java
// kinotic-github/.../api/services/GitHubProjectRepoService.java:12
// @Publish TODO: not exposed until we are ready to use by worker nodes and security has been finalized
public interface GitHubProjectRepoService {
    CompletableFuture<GitHubRepoToken> issueRepoToken(String organizationId, String projectId);
    ...
```

**No task/run record exists anywhere.** `Progress`/`ProgressType`
(`kinotic-domain/.../api/model/Progress.java`) is declared and referenced by nothing. The Grind
engine (`kinotic-orchestrator/.../api/grind/`) is a complete in-memory Reactor step engine with
**no ids, no persistence, no timestamps** — nothing survives the `Flux` subscription. The only
persisted execution history is `MigrationExecutor.MigrationRecord`.

**The streaming and storage primitives for tasks already exist.** A `@Publish`ed method
returning `Flux<T>` streams to TS clients via `invokeStream`
(`ServiceInvocationSupervisor.java:299-319`); `DefaultLogService`/`DefaultLokiClient` show the
authorize-then-stream shape. For append-only history the repo's own docs prescribe ES data
streams (`website/content/02.platform/10.system-migrations.md:79`), the migration grammar
supports `CREATE DATA STREAM`, and the write path exists:

```java
// kinotic-domain/.../internal/api/services/CrudServiceTemplate.java:93
public <T> CompletableFuture<IndexResponse> appendToDataStream(String dataStreamName, T document)
```

**"Exactly one node does X" has an established idiom.** `DefaultServiceDirectory.java:177`
deploys `ServiceLivenessUpdater` as an Ignite cluster singleton; its Javadoc names the design:
*signal → verify → write* — a change event is an invalidation trigger, never a value; a periodic
reconcile backstops missed events. That is precisely the concurrency model requirement 1 needs.

**In-process scoped execution has an established idiom.** Participants bind to the Vert.x
context (`SecurityContext.setParticipant`, `SecurityContext.java:63`);
`ServiceInvocationSupervisor.java:253-260` does it per RPC and `KinoticTestBase.runAsOrganization`
does it for direct bean invocation. Org scoping then flows through
`AbstractOrganizationScopedService.requireOrganizationId()` and the composite-id repositories.

---

## Design

### The shape in one paragraph

A new `kinotic-deployment` module hosts a **deployment reconciler** running as an Ignite cluster
singleton. Push webhooks are only signals: on any `push` signal for `develop` (or on its
periodic reconcile), the reconciler asks GitHub for the **current head** of `develop`, compares
it to the project's persisted `ProjectDeployment` record, and if they differ runs one
deployment pipeline for that project — serialized per project, coalescing further signals into
a dirty flag. The pipeline is a fixed sequential list of steps recorded on a persistent `Task`:
fetch the repo tarball, extract entity definitions/named queries/migrations from the source,
apply them **in-process** through the existing `EntityDefinitionService` /
`NamedQueriesDefinitionService` / `MigrationService` beans (no wire credentials exist to leak),
then create/update the project's `Workload`s so the vm-manager nodes stage the new commit's
source and restart the VMs. Every step appends its output to a task-log data stream and the
`TaskService` streams both status and logs live to the UI.

### Why pull-from-platform, not CI-push

The developer repo gets no workflow files and no secrets. Three reasons, all grounded in
current code: (a) the docs and template README already promise it
(`website/content/01.apps/02.quick-start.md:87`); (b) the CLI's auth is an interactive device
grant (`CliAuthenticator.ts`) with no headless machine-credential path —
`docs/NavidNotes.md:188` records machine credentials as future work, so CI-side `kinotic sync`
would require building a new credential system *first*; (c) requirement 7 is trivially
satisfied when the definitions/migrations apply step is in-process: there is no credential at
all, only a participant bound to a Vert.x context inside kinotic-server.

### Trigger and convergence (requirement 1)

The webhook fan-out today emits only per-project CRIs, which a global listener cannot
subscribe to. Add the aggregate address alongside (one line in the loop in
`DefaultGitHubWebhookEventService.handleRepoEvent`), keeping the per-project events untouched:

```java
// DefaultGitHubWebhookEventService.handleRepoEvent — target shape
// evt://github/push  (aggregate, fixed address a platform listener can subscribe to)
eventBusService.send(Event.create(aggregateCri(event.getEventType()), payload));
// evt://github/push/<org>/<project>  (existing per-project fan-out, unchanged)
eventBusService.send(Event.create(cri, payload));
```

The reconciler singleton (deployed exactly like `DefaultServiceDirectory.java:177` deploys
`ServiceLivenessUpdater`) listens on `evt://github/push`, filters
`payload.ref == "refs/heads/" + DEPLOY_BRANCH` (`DEPLOY_BRANCH = "develop"`, a constant per the
Properties rule — it does not vary by environment), and treats the delivery as a **signal
only**. The payload's `after` SHA is never trusted for ordering. Verification queries GitHub
for the ref head at processing time:

```java
// signal → verify → write, per the ServiceLivenessUpdater pattern
CompletableFuture<String> head = repoService.issueRepoToken(orgId, projectId)      // contents:read, ~1h, single repo
        .thenCompose(token -> apiClient.getRefSha(token.getToken(), repoFullName,
                                                  "heads/" + DEPLOY_BRANCH));       // new small client method
```

This makes every delivery idempotent and the end state convergent: duplicates and out-of-order
deliveries all resolve to "deploy the current head"; a burst of pushes coalesces (if a run is
in flight for the project, set a dirty flag; on completion re-verify and loop until head ==
deployed). A periodic reconcile pass re-checks only projects whose record is non-converged
(`desired != deployed`, or a `RUNNING` task orphaned by a server restart → mark it `FAILED`,
re-run) — it does not poll GitHub for converged projects, so there is no rate-limit exposure.
Per-project serialization also resolves the `MigrationExecutor.java:80` concurrency FIXME for
this path, cluster-wide, without a new lock.

The durable convergence state is one small record (kinotic-domain, `kinotic_project_deployment`
index, id = projectId so there is exactly one per project):

```java
@Getter @Setter @Accessors(chain = true) @NoArgsConstructor
public class ProjectDeployment implements ProjectScoped<String> {
    private String id;                  // == projectId
    private String organizationId;
    private String applicationId;
    private String projectId;
    private String desiredCommitSha;    // last verified head of develop
    private String deployedCommitSha;   // head of develop at the last successful pipeline run
    private String currentTaskId;       // Task of the in-flight or most recent run
    private Date created;
    private Date updated;
}
```

This is the development-environment counterpart of the `ApplicationDeployment` record the
multi-environment plan introduces in its Phase 5 — per-project and code-centric where that one
is per-application and definition-centric. When `Environment` exists, `ProjectDeployment`
gains an `environmentId` and the two records compose; nothing here assumes a single
environment beyond omitting that field.

### The pipeline (requirements 2, 3, 4)

A fixed, sequential, fail-fast list — deliberately **not** Grind. Grind was evaluated and
rejected for this: it is in-memory only (no ids, no persistence — requirement 5 is exactly
persistence), and a strictly sequential six-step list needs no DAG, no parallelism, no nested
scopes. The multi-environment plan already blesses this fallback ("a plain service method
executing the steps sequentially is acceptable — do not build a *third* flow mechanism"); the
plain runner is that sanctioned second mechanism, and the persistent `Task` record it writes is
reusable by Promotion later.

Steps, in order (each is one entry on the `Task`):

1. **Resolve** — mint the repo token (`issueRepoToken`), read the head of `develop`
   (`getRefSha`). If `head == deployedCommitSha` and the last task succeeded: converged, no-op,
   no task is created.
2. **Fetch** — `apiClient.downloadTarball(token, repoFullName, sha)` (exists; used by the repo
   provisioner today), unpack to a scratch dir.
3. **Extract** — run the entity compiler (below) over the source: entity `ObjectC3Type`
   schemas, named-query definitions, and read `migrations/V<n>__<desc>.sql` files.
4. **Apply definitions** — in-process, with a platform-minted `OrganizationParticipant` for the
   project's org bound to the Vert.x context (the `runAsOrganization` mechanism). Order matches
   the CLI (`synchronize.ts:94-97`): named queries first, then per entity
   `create`-or-`save` + `publish` — the same upsert semantics `kinotic sync` has today,
   including its published-entity constraint (additive field changes only). Withdrawn entities
   are **not** auto-deleted (`unPublish` destroys the data index; parity with the CLI, which
   never deletes).
5. **Apply migrations** — `MigrationService.executeMigrations` with all `V*.sql` from the repo;
   `MigrationExecutor` skips applied versions, so this is idempotent. Any statement failure
   fails the step (and the run) with the ES error detail; the code step below never executes —
   which is requirement 2's ordering guarantee, enforced by sequence.
6. **Deploy code** — enumerate `packages/microservices/*` in the source; diff against the
   project's existing `Workload`s: `deployWorkload` for new microservices,
   stop + restage + restart for existing ones (in place, same node, same VM disk — an update,
   not a teardown), `destroyWorkload` for removed ones. Detail below.
7. **Finalize** — set `deployedCommitSha = sha`, task `SUCCEEDED`. If the dirty flag was set
   during the run, go back to step 1.

A failed run leaves the previously running workloads untouched and serving the old code; the
record stays non-converged so the next signal (or reconcile pass) retries. Steps 2–6 are all
idempotent, so a crash mid-run needs no compensation logic — the re-run repeats them.

First deployment (requirement 4) needs no special casing: an empty workload diff creates
everything, `MigrationExecutor` starts from version 0, and definitions upsert from empty. The
only bootstrap gap is the branch itself: the repo provisioner initializes only the default
branch, so it additionally creates `develop` at the root commit (`createRef` exists —
`DefaultGitHubApiClient.createRef`, already idempotent on "Reference already exists").

### Extracting definitions from source (the one genuinely new mechanism)

Entity definitions are TypeScript classes with stage-3 decorators, read from the **AST** — the
decorators are explicit no-op markers (`KinoticPersistenceDecorators.ts:7-8`: "source-level
markers the Kinotic CLI reads from the TypeScript AST"). The conversion lives in the CLI
(`kinotic-js/kinotic-cli/src/internal/Utils.ts:87-158`, ts-morph based). The server is Java; it
cannot parse TS — but the platform already embeds GraalJS to run a published npm bundle for
exactly this kind of job:

```gradle
// kinotic-github/build.gradle:59-61 — the precedent: the spawn renderer is an npm tarball
// resolved via an ivy-mapped registry and executed in GraalJS (GraalJsSpawnRenderer)
dependencies {
    spawnRenderer "@kinotic-ai:spawn:${kinoticSpawnVersion}@tgz"
}
```

Do the same for extraction: lift the CLI's conversion core (entity discovery, decorator →
C3Type conversion, named-query extraction, static parse of `.config/kinotic.config.ts` — a
plain object literal) into a workspace package (e.g. `@kinotic-ai/entity-compiler`) consumed by
**two real callers**: the CLI (which keeps `generate`/`sync` behavior identical) and a GraalJS
bundle target executed by kinotic-deployment over the unpacked tarball. The Rule of Three is
satisfied by two production consumers; the extraction returns C3 JSON that Jackson deserializes
straight into `EntityDefinition.schema` (`ObjectC3Type` — kinotic-idl owns both sides of that
contract already).

Two properties of this choice worth stating: extraction is **static** — no developer code
executes on the server (ts-morph never runs the program), which is a hard security property —
and it reads the pushed source directly, so there is no staleness window. The alternatives were
rejected: reading committed `.config/c3/*.json` artifacts silently deploys stale definitions
whenever a developer edits an entity and forgets `generate`; running `kinotic sync` in a
builder VM requires the machine-credential system that doesn't exist yet and puts a
platform-scoped credential inside a VM that also executes `kinotic.config.ts` (developer code).
Risk to validate early in the phase: ts-morph (the full TS compiler) under GraalJS is heavier
than liquid rendering — acceptable here because the singleton serializes runs, but benchmark it
in the phase and keep the bundle API narrow so a Node-sidecar fallback would not change any
Java contract.

### Running the code (requirement 3, 7)

Workloads run from source on the platform's runtime image; no image build pipeline is
introduced. `Workload` gains the source linkage:

```java
// kinotic-domain/.../api/model/workload/Workload.java — new fields
private String projectId;      // with organizationId/applicationId already present
private String commitSha;      // the commit whose source this VM runs
```

The deploy step creates one workload per microservice directory:

```java
Workload w = new Workload(project.getId() + "-" + msName, deploymentProperties.getRuntimeImage())
        .setOrganizationId(project.getOrganizationId())
        .setApplicationId(project.getApplicationId())
        .setProjectId(project.getId())
        .setCommitSha(sha)
        .setCmd(List.of("/kinotic/run-microservice.sh", "packages/microservices/" + msName));
```

`kinotic.deployment.runtimeImage` is a property (it varies per platform deployment — it pins a
published image version). The image is a small platform-owned OCI image: Bun + an entrypoint
script that runs `bun install` then the microservice entry, with stdout/stderr redirected to
`/var/log/kinotic/<name>.log` — which makes the existing Alloy → Loki → `LogService`
pipeline (`GUEST_LOG_DIR`, `BoxliteProvider.ts:8-14`) work for customer app logs with zero new
plumbing.

**Source staging is the vm-manager's job, host-side** — this is where requirement 7 lands.
`GitHubProjectRepoService` finally gets its `@Publish` (the `:12` TODO's "worker nodes ready"
moment): zone stays `os-api`; authorization accepts the existing `OrganizationParticipant` path
*and* `SystemParticipant` (vm-managers authenticate as SYSTEM today via
`KINOTIC_SERVER_LOGIN`/`TOKEN`, and `ZoneRules` already lets SYSTEM send to every zone). When a
workload carries `projectId` + `commitSha`, the vm-manager — before boot — calls
`issueRepoToken`, downloads the tarball at that commit **on the host**, and stages it into the
per-workload host directory mounted at `/app`:

```ts
// vm-manager DefaultVmManager.startWorkload — target shape (host side, before provider.start)
if (workload.projectId) {
    const repoToken = await this.repoService.issueRepoToken(workload.organizationId, workload.projectId)
    await stageSource(repoToken, workload.commitSha, this.sourceDirFor(workload.id))   // host fetch + unpack
}
```

The token is used and discarded host-side; it is **never** written into `Workload.environment`
and never enters the guest — the developer's code sees only its own runtime env. Redeploys are
in-place: the pipeline updates `commitSha` and restarts; the vm-manager restages the host
directory when the staged SHA differs and boots the **same VM** (same node, same disk, same
mounts) — a restart, not a rebuild. `VmManagerProxy.restartWorkload` changes to carry the
`Workload` (wire contracts are free to reshape while `kinoticVersion` is `-SNAPSHOT`) so the
node sees the new SHA without a second read.

What the guest *does* get in `Workload.environment`: `KINOTIC_HOST`/`KINOTIC_PORT` for the
gateway (replacing the template's hardcoded `localhost:58503`) and the application's runtime
credentials — which are the application's own identity, not deployment credentials, and are
*supposed* to be visible to app code. How those app credentials are minted is open question 4:
no machine-credential type exists (`docs/NavidNotes.md:188`), and without one the template
microservice cannot authenticate the STOMP handshake at all.

### Task records (requirements 5, 6)

New domain models in `kinotic-domain/.../api/model/task/` (three related files justify the
package), persisted in a `kinotic_task` index + a `kinotic_task_log` data stream (both added to
`V1__init.sql`, edited in place per the SNAPSHOT rule):

```java
@Getter @Setter @Accessors(chain = true) @NoArgsConstructor
public class Task implements ProjectScoped<String> {
    private String id;                 // UUID, minted by the producer
    private String organizationId;
    private String applicationId;
    private String projectId;
    private String name;               // e.g. "Deploy my-project @ ab12f34"
    private TaskStatus status;         // PENDING, RUNNING, SUCCEEDED, FAILED
    private List<TaskStep> steps;      // embedded; written by the single producer
    private Date created;
    private Date started;
    private Date finished;
}
```

```java
@Getter @Setter @Accessors(chain = true) @NoArgsConstructor
public class TaskStep {
    private String name;               // "Fetch source", "Apply migrations", ...
    private TaskStatus status;         // same vocabulary as the task itself
    private Date started;
    private Date finished;
    private String message;            // short outcome / error detail
}
```

`TaskStatus` is one enum shared by task and step — same value set, not speculative generality.
There is deliberately **no** task `type` discriminator, no retry policy, no step registry: a
task is named data produced by whoever runs it. Deployment is the only producer; when
Promotion (or another operation) becomes the second, whatever producer-side helper is worth
sharing gets extracted *then*.

Log lines are append-only documents in the data stream (the shape the platform docs prescribe
for append-only time-series):

```sql
-- V1__init.sql — task records + step logs (90d retention on the stream)
CREATE TABLE IF NOT EXISTS kinotic_task ( ... );
CREATE DATA STREAM kinotic_task_log ("@timestamp" DATE, taskId KEYWORD, stepName KEYWORD, message TEXT)
    WITH (DATA_RETENTION = '90d');
```

The read surface is a `@Publish`ed, read-only service in kinotic-os-api (next to `LogService`,
which it mirrors in shape — this is *not* an `IdentifiableCrudService`, because tasks are
system-written and must not be client-writable):

```java
@Publish
public interface TaskService {
    CompletableFuture<Task> findById(String taskId);
    CompletableFuture<Page<Task>> findAllForProject(String projectId, Pageable pageable);
    /** Emits the current task immediately, then every status/step transition until terminal. */
    Flux<Task> watch(String taskId);
    /** Replays persisted log lines, then live lines until the task reaches a terminal status. */
    Flux<TaskLogEntry> tailLog(String taskId);
}
```

Authorization follows `DefaultLogService`: resolve the record first, org participants only for
their own org, SYSTEM sees all — authorize before subscribing. Live delivery rides the cluster
event bus: the producer `send`s a task snapshot to `evt://task/<taskId>` on every transition
and each log line to `evt://task/<taskId>/log` (alongside the durable
`appendToDataStream` write); `watch`/`tailLog` bridge `eventBusService.listen(cri)` into the
returned `Flux` the same way `DefaultLokiClient.tail` bridges a WebSocket. Restart-survival
and late queries come from the index + data stream, not the bus.

Frontend: a Deployments tab on the project detail page (`CrudTable` over
`findAllForProject`, the `ApplicationDetails.vue` tab pattern) and a task detail page — step
list rendered with the already-themed-but-unused PrimeVue `Stepper`/`Timeline` tokens
(`kinotic-frontend/src/theme/`), live via `invokeStream` exactly as `DataInsights.vue:265-290`
consumes `Flux<InsightProgress>` today, plus a log pane fed by `tailLog`. TS proxies land in
`@kinotic-ai/os-api` next to `ILogService.ts` (its `tail` is the existing
`invokeStream` exemplar).

### Security summary (requirement 7)

| Need | Credential | Scope | Lifetime | Where it lives |
|---|---|---|---|---|
| Read repo (server: verify head, fetch tarball) | GitHub App installation token | one repo (`repository_ids`), `contents:read` | ~1 h, minted per run | server memory only |
| Read repo (vm-manager: stage source) | same, via `issueRepoToken` | one repo, `contents:read` | ~1 h | node host memory; never in guest env |
| Apply definitions + migrations | **none** — in-process bean calls with an `OrganizationParticipant` bound to the Vert.x context | that org/project by construction of the calls | per run | never leaves the JVM |
| App runtime → gateway | the application's own identity (open question 4) | that application (`ZoneRules`: `app-api` + own `app.<org>.<app>` zone) | app-owned | guest env — visible to app code *by design* |

The developer's application code can neither read nor configure any deployment credential: the
repo token never enters the guest, the definitions/migrations step has no credential, and no
`kinotic.config.ts` or other project code ever executes on the server (static AST extraction
only) or in a credential-bearing context.

### Module layout

`kinotic-deployment` (new library module — the pipeline consumes kinotic-github,
kinotic-persistence, and kinotic-orchestrator, which no existing module may depend on together
without cycles: github already depends on os-api, and persistence/orchestrator are siblings):

```
kinotic-deployment/
  src/main/java/org/kinotic/deployment/
    KinoticDeploymentLibrary.java            // @ConditionalOnProperty kinotic.disableDeployment (existing idiom)
    api/config/DeploymentProperties.java     // runtimeImage (per-deployment); DEPLOY_BRANCH stays a constant
    api/services/ProjectDeploymentService.java   // @Publish read-only: findByProjectId (os-api zone)
    internal/api/services/DeploymentReconciler.java   // Ignite Service (cluster singleton, signal→verify→write)
    internal/api/services/DeploymentPipeline.java     // the sequential steps; writes Task + logs
    internal/api/services/EntityCompilerRunner.java   // GraalJS bundle execution (GraalJsSpawnRenderer pattern)
  src/main/resources/META-INF/spring/...AutoConfiguration.imports   // standard module triple
```

Placement of the shared pieces follows the ownership rules already in force: `Task`/`TaskStep`/
`TaskStatus`/`TaskLogEntry` + `TaskRepository` + `ProjectDeployment`(+repo) in **kinotic-domain**
(domain objects with the other OS models); `TaskService` in **kinotic-os-api** (org-facing read
surface, like `LogService`); the producer in **kinotic-deployment**. kinotic-server adds
`implementation project(':kinotic-deployment')` and `project(':kinotic-orchestrator')`.
The multi-environment plan's app-gateway profile must then set `kinotic.disableDeployment: true`
and `kinotic.disableOrchestrator: true` (its Phase 4 zone-allowlist guard would catch a miss —
`DeploymentReconciler` publishes nothing, but `ProjectDeploymentService`/`TaskService` are
`os-api`-zoned).

The orchestrator gains the `kinotic.disableOrchestrator` gate it is missing when it is wired in
(the multi-environment plan flags this same wiring as its open question 3 — confirm with Navid
once, here, since this plan needs it first).

---

## Phases

Phase sizing follows the working convention: ~10 files per phase, stop for review at every
boundary.

### Phase 1 — Task records + read API

- kinotic-domain: `api/model/task/{Task, TaskStep, TaskStatus, TaskLogEntry}`,
  `internal/api/repositories/TaskRepository` (extends `AbstractProjectScopedRepository`,
  index `kinotic_task`), `V1__init.sql` additions (table + `kinotic_task_log` data stream).
- kinotic-os-api: `api/services/TaskService` (`@Publish`, read-only, `watch`/`tailLog` per the
  design) + `internal/api/services/DefaultTaskService` (authorize-then-stream, event-bus
  bridge).
- kinotic-js: `ITaskService.ts` proxy + models in `@kinotic-ai/os-api`, wired into
  `OsApiPlugin`.
- Tests: kinotic-test integration — create a task via the repository, read/watch/tail through
  the published interface with `runAsOrganization`, assert org isolation (a second org cannot
  read it).

### Phase 2 — Tasks UI

- kinotic-frontend: Deployments tab on the project detail page (`CrudTable` +
  `findAllForProject` adapter), task detail page (step timeline + live status via
  `invokeStream(watch)`, log pane via `tailLog`), status `Tag` severity mapping per the
  `MembersPage.vue` idiom.
- Docs: task/deployment observability page under `website/content/`.

### Phase 3 — Entity compiler package (kinotic-js only)

- Extract the CLI's conversion core (`Utils.convertAllEntities`, named-query extraction,
  static `kinotic.config.ts` parse) into `@kinotic-ai/entity-compiler`; the CLI consumes it
  with byte-identical `generate`/`sync` output (existing CLI tests are the regression net).
- Add the GraalJS-targeted bundle build. Benchmark ts-morph under GraalJS here — this is the
  phase's exit criterion, before any Java depends on it.

### Phase 4 — kinotic-deployment: state sync (definitions + migrations, no code yet)

- The new module: library class + gate, `DeploymentProperties`, reconciler singleton +
  event-bus listener (aggregate `evt://github/push` emit added in kinotic-github),
  `ProjectDeployment` model/repo/migration, pipeline steps 1–5 + 7 (resolve, fetch, extract,
  apply definitions, apply migrations, finalize) writing Task records + logs,
  `EntityCompilerRunner` (spawn-renderer pattern), `getRefSha` on `GitHubApiClient`.
- The webhook body-limit fix in kinotic-api-gateway (mount the webhook route before the global
  16 KiB `BodyHandler`, or exempt its path) + a signed-payload test that would have caught it.
- Repo provisioner creates the `develop` branch at initialization.
- Template (`kinotic-tpl-isomorphic-ts`): add `migrations/` with a starter example.
- Tests: kinotic-test integration with GitHub stubbed at the `GitHubApiClient` boundary (the
  multi-environment plan's sanctioned seam): signed push → task with steps → definitions
  published + migrations applied against real ES; duplicate + out-of-order delivery test
  asserting single converged run; restart-orphan test (RUNNING task → reconcile marks FAILED
  and re-runs).

This phase alone makes the quick-start's promise true for entities and migrations.

### Phase 5 — Code deployment

- Wire kinotic-orchestrator into kinotic-server + `kinotic.disableOrchestrator` gate (Navid's
  confirmation from open question 1).
- `Workload.projectId`/`commitSha` + migration column additions; pipeline step 6 (workload
  diff, deploy/restage-restart/destroy); `VmManagerProxy.restartWorkload(Workload)` contract
  change.
- Publish `GitHubProjectRepoService` (SYSTEM + org authorization); vm-manager host-side source
  staging; the platform runtime image (Bun + run script with `/var/log/kinotic` redirection).
- Template: `main.ts` reads `KINOTIC_HOST`/`KINOTIC_PORT` (+credentials per open question 4).
- Tests: vm-manager staging behavioral test (local tarball fixture); orchestration integration
  test with the vm-manager stubbed at the `VmManagerProxy` boundary asserting
  create-vs-restart-vs-destroy decisions and that the repo token appears nowhere in the
  persisted `Workload`.

### Phase 6 — Surfacing + docs reconciliation

- Project list/detail deployment status (from `ProjectDeployment` via
  `ProjectDeploymentService`), link to the latest task.
- Reconcile `website/content/**`: `01.apps/02.quick-start.md`,
  `01.apps/07.deployment/01.workflow.md` (rewrite to the shipped shape: `develop` →
  development, tasks UI; keep staging/production clearly marked as not yet built),
  `02.environments.md`, template README.
- End-to-end pass in the e2e compose (webhook-simulated push through to task SUCCEEDED; the
  code-deploy step exercised only where a vm node exists, gated like other
  environment-dependent tests).

---

## Decided (design positions taken by this plan)

- Pull-from-platform; no CI and no secrets in developer repos.
- The reconciler is an Ignite cluster singleton using signal → verify → write with periodic
  reconcile; GitHub's ref head at verification time is the only ordering authority.
- Definitions/migrations apply in-process — no deployment credential exists on the wire.
- Static AST extraction via a GraalJS-bundled `@kinotic-ai/entity-compiler`; developer code
  never executes on the server.
- Plain sequential pipeline recorded on a persistent `Task`; Grind stays unused (no
  persistence, and this flow needs no DAG).
- Run-from-source on a platform runtime image; no image build pipeline in this plan.
- Task model is generic data (no type discriminator, no framework); deployment is its only
  producer until a second one exists.

## Open questions for Navid (answer before/at handoff)

1. **Wire kinotic-orchestrator into kinotic-server** — the multi-environment plan's open
   question 3, needed by Phase 5 here. Confirm the orphan status is not deliberate.
2. **Run-from-source acceptable for the development environment?** Building OCI images per
   push is deliberately excluded; if image builds are wanted sooner, Phase 5 changes shape
   (and the Firecracker build-isolation story enters scope).
3. **Extraction approach** — this plan commits to the GraalJS `entity-compiler` bundle. If the
   Phase 3 benchmark disqualifies GraalJS+ts-morph, the fallback is reading committed
   `.config/c3/*.json` artifacts (with the documented staleness caveat) or a Node sidecar;
   which fallback is preferred?
4. **Application runtime credentials.** The template microservice authenticates the STOMP
   handshake as *what*? `docs/NavidNotes.md:188` wants a non-human credential type; interim
   option is a platform-seeded APPLICATION-scoped `ParticipantIdentity` + secret injected via
   `Workload.environment`. Without one of these, deployed code cannot connect at all — this
   blocks the end of Phase 5.
5. **Microservice resource defaults** — vcpus/memory/disk per workload for the development
   environment (constants vs per-project settings; this plan assumes constants).
6. **`develop` branch convention** — confirmed as a constant, with the provisioner creating it
   at repo initialization? Or should the deploying branch be recorded on `Project`?
7. **Task retention** — the plan sets 90d on the `kinotic_task_log` data stream and never
   deletes `kinotic_task` docs. Acceptable, or should task records age out too?

## Guardrails for the implementer

- One phase per approval; report and wait (see the stop rule at the top).
- Re-verify every `path:line` anchor before editing; don't trust the plan over the tree.
- CLAUDE.md rules apply in full: Lombok, enums over strings, `api/`/`internal/` layout, one
  top-level type per file, no version literals in module `build.gradle`, migrations edited in
  place while `-SNAPSHOT`, docs synced in the same change, and the smells catalog — in
  particular no speculative task-framework features (requirement 6 is explicit about this) and
  no test-only seams.
- The multi-environment plan (`docs/future-prompts/Multi-environment architecture.md`) is the
  adjacent map: keep `ProjectDeployment` compatible with its Phase 5 records, add the two new
  module gates to its Phase 4 profile table when that work lands, and let its Phase 9
  Promotion reuse `Task` + the pipeline's step helpers only when it actually exists.
