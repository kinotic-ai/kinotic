# Continuous deployment of Kinotic apps on push to `develop`

This is a phased implementation plan. Each phase compiles and passes tests on its own and is a
reasonable PR boundary. Current-state claims below were verified against `develop` at `2e7f08a`
("Rename IamUser to ParticipantIdentity (#375)", 2026-08) — re-verify with fresh inspection
before acting on any of them; files move, and several load-bearing facts here (the orchestrator's
orphan status, the un-`@Publish`ed repo-token service) are explicitly expected to change.

**STOP AT EVERY PHASE BOUNDARY.** When a phase is complete (implemented, tested, committed,
pushed), report what was done and wait for Navid's explicit approval before starting the next
phase. Do not begin any work belonging to a later phase while waiting.

## Goal

When a developer lands changes on the `develop` branch of their project's GitHub repository, the
platform automatically deploys them: the project's entity definitions and migrations are applied
to the server, and the project's Bun microservices run (or restart) on a Kinotic VM node — with
no manual action beyond the push. We follow gitflow; `develop` is the only deploying branch for
now, and production promotion is out of scope (see `Multi-environment architecture.md` Phase 9
for where that will eventually attach).

Every deployment run is visible in the Kinotic UI as a persistent task with discrete steps,
per-step status, and live/historical container logs. The task record and its UI are the
deliberately reusable piece; the deploy pipeline is their first and only producer.

## Deployment shape

One "app runtime" container per project runs the Bun code for all of that project's
microservices. Deploys after the first do **not** stop this container: a short-lived "build"
container on the same node updates a shared project folder and syncs entity definitions to the
server, and the runtime container detects the completed deploy and restarts its child processes.

```
push to develop
  └─ GitHubWebhookHandler (exists) ─► evt://github/push/<orgId>/<projectId> (exists, no consumer)
       └─ NEW deployment trigger: filter ref == refs/heads/develop, coalesce per project,
          create TaskExecution record, run pipeline
            ├─ Step "checkout + sync"  BUILD workload, pinned to the app's node, shared
            │                          hostPath volume: git reset to head of develop,
            │                          bun install, kinotic sync, then write marker file
            ├─ Step "start runtime"    first deploy only: RUNTIME workload on the same
            │                          node + hostPath (later deploys: runtime restarts
            │                          itself off the marker; this step is a no-op)
            └─ TaskExecution updated per step; each step records its workloadId
                 └─ UI: task list + detail, per-step log tail via LogService.tail(workloadId)
```

## Verified current state

**Trigger side — the hook exists, unconsumed.**
- `GitHubWebhookHandler` (`kinotic-github/.../internal/api/rest/GitHubWebhookHandler.java`)
  mounts `POST /api/github/webhook`, HMAC-verifies, and acks 204 *before* processing.
- `DefaultGitHubWebhookEventService` handles `installation` / `installation_repositories`
  explicitly; every other event type (including `push`) falls to the `default` branch, which
  republishes the raw payload per matching project as
  `evt://github/<eventType>/<orgId>/<projectId>` (`DefaultGitHubWebhookEventService.java:133-147`,
  CRI grammar documented at `EventConstants.java:100-105`). Nothing anywhere subscribes to these
  events. There is no delivery dedup; the service Javadoc says consumers must be idempotent.
- Repo ↔ project association is `ProjectRepository.findByRepoFullName` (returns a list — the
  same repo can back projects in more than one org, and dispatch fans out to all of them).
- `Project.repoDefaultBranch` is a provision-time snapshot, never refreshed. The provisioner
  (`GitHubProjectRepoProvisioner`) pushes a single root commit on the default branch only —
  **no `develop` branch exists** in a freshly provisioned repo.
- `GitHubProjectRepoService` (`issueRepoToken`, `createTag`, `createBranch`) is deliberately
  un-`@Publish`ed: `// @Publish TODO: not exposed until we are ready to use by worker nodes and
  security has been finalized`. `issueRepoToken` already builds the clone URL + short-lived
  installation token.

**Workload side — one placement lever, hostPath is the share key.**
- `WorkloadOrchestrationService.deployWorkload` always picks its own node via
  `findAvailableNode(cpu, mem, disk)` and overwrites any caller-supplied `nodeId`
  (`DefaultWorkloadOrchestrationService.java:34-59`). The only way to pin a workload to a
  specific node is the RPC underneath: `VmManagerProxy.startWorkload(@Scope String nodeId,
  Workload workload)` — the `@Scope` param routes to the vm-manager registered with that node id.
- Two workloads share a folder by declaring the same `hostPath` in `Workload.volumeMounts`
  (`VolumeMount`: hostPath/guestPath/readOnly). `BoxliteProvider.buildBoxOptions` passes them
  straight through as virtio-fs mounts. There is no named-volume abstraction; the hostPath
  string is the join key.
- boxlite behavioral findings (all proven in `kinotic-js/boxlite-test/`, see its README):
  - virtio-fs **data** is coherent across boxes on the same node; **inotify events are not** —
    `bun --watch` never fires on a cross-box edit (`volume-share-test.ts`).
  - chokidar with `{ usePolling: true }` detects cross-box edits, including nested files
    (~4s at `interval: 300`), and can drive a kill-and-respawn reload (`volume-poll-test.ts`).
    None of this machinery exists in production `vm-manager` code yet.
  - Entrypoint stdout/stderr is **not host-visible**. Workloads must write log files under the
    always-mounted `GUEST_LOG_DIR = '/var/log/kinotic'` (`BoxliteProvider.ts:8-14`).
  - `stop()` is a pause; restarting a box re-runs the recorded entrypoint, and options passed to
    a reused box are silently ignored. Run-to-completion entrypoints zombie the box.

**`kinotic sync` — entity definitions only, human-authenticated, no completion signal.**
- The CLI `sync` command (`kinotic-js/kinotic-cli/src/commands/synchronize.ts`) pushes C3
  entity schemas + named queries and applies `V<n>__*.sql` migrations. It does **not** upload
  project source files, emits no server-side completion event (the only related server events
  are per-entity `CacheEvictionEvent`s, which are cache-invalidation semantics, not "sync
  done"), and authenticates via the OAuth device grant (`CliAuthenticator.ts`) — interactive,
  unusable from a build container as-is.
- The template README's claim that the server reads entity definitions from GitHub is
  aspirational — no server code reads `.config/c3/**`. (Known doc defect, tracked separately.)

**Grind — sequencing without persistence, in an orphan module.**
- `kinotic-orchestrator` is on no deployable's classpath (`kinotic-server/build.gradle` omits
  it), so both `WorkloadOrchestrationService` and the grind `JobService` are unreachable at
  runtime today. Wiring it in is open question 3 of `Multi-environment architecture.md`; this
  feature forces the answer.
- Grind (`api/grind/`) gives Task/Step/JobDefinition composition and a
  `Flux<Result<?>>` event vocabulary (`VALUE/NOOP/DIAGNOSTIC/PROGRESS/DYNAMIC_STEPS/EXCEPTION`)
  but has no job id, no record, no persistence — history exists only while a subscriber is
  attached. `JobService` is not `@Publish`ed. Per `Multi-environment architecture.md:646-648`:
  verify it before committing to it; a plain sequential service is the sanctioned fallback —
  do not build a third flow mechanism.

**Log plumbing — complete pipe, zero consumers.**
- Guest `*.log` files under `/var/log/kinotic` → per-node Grafana Alloy (`AlloyManager.ts`,
  config regenerated per workload change, tenant label → `X-Scope-OrgID`) → multi-tenant Loki →
  `LogService.tail(workloadId)` / `history(LogQuery)` (`@Publish`,
  `kinotic-os-api/.../LogService.java`, authorization via the Workload record's
  organizationId) → generated TS proxy (`os-api/src/api/services/ILogService.ts`).
- No UI consumes it, and both methods return raw Loki wire bytes — there is no client-side
  Loki-frame parser yet.
- `kinotic-frontend` has no task/job/progress/workload UI of any kind. The streaming transport
  it would use is proven: `@Publish` method returning `Flux<T>` → `serviceProxy.invokeStream`
  → RxJS `Observable` (`core/src/api/ServiceRegistry.ts`).

## Design decisions

**The marker file is the deploy commit point; the runtime polls only the marker.** The
already-tested polling approach watches the whole tree, so the runtime would redeploy the moment
the build container's `git reset` starts writing files — before `kinotic sync` has run. Fix is a
contract change, not a mechanism change: the runtime's poller watches exactly one file
(`<project guest path>/.kinotic/DEPLOY`), and the build step writes it (write-temp-then-rename,
so a poll never reads a partial file) only after sync succeeds. Checkout churn becomes invisible,
ordering is guaranteed (requirement: code must never serve against a server that doesn't yet
know its entity definitions), and polling cost drops from stat-ing a tree to stat-ing one file,
so a ~1s interval is fine. In-place checkout (`git fetch && git reset --hard <sha>`) into the
shared folder stays, as validated. A `releases/<sha>/` layout with the marker pointing at the
active release would add atomic cutover and instant rollback — that is the upgrade path when
rollback becomes a requirement, not part of this work (YAGNI).

**Deploy the head of `develop` at execution time, not the pushed SHA.** With no delivery dedup
and ack-before-process, replays and rapid successive pushes must be harmless. Checking out the
branch head makes every deploy idempotent and naturally coalescing; the trigger additionally
serializes per project (one running deploy per project; at most one queued, latest wins).

**`develop` is a constant, not a property.** Same value in every environment until the
production story exists (house Properties rule). The branch→environment mapping entity comes
with promotion work, designed against the second concrete case.

**TaskExecution is a persistent platform entity following the `Workload` pattern** — a new
`kinotic_task_execution` index declared in `V1__init.sql` (editable in place while `-SNAPSHOT`),
an `AbstractRepository` subclass beside `WorkloadRepository`, and a `@Publish` service exposing
CRUD reads plus a `Flux<TaskExecution>` live stream per project (mirroring `LogService`'s
shape). Fields, roughly: `id`, `organizationId`, `applicationId`, `projectId`, `type` (enum,
one value today is fine — it is a persisted wire discriminator, not a speculative one),
`status` (enum), `steps` (`JSON NOT INDEXED`; each step: name, status, workloadId, started,
finished, error), `created`, `updated`. The schema is engine-agnostic on purpose: that is what
makes it reusable later without building a task framework now. Explicitly **not** built: task
type registries, retry policies, DAGs, cron — nothing until a second real producer exists.

**The pipeline is a plain sequential service updating the TaskExecution between steps.** Grind
would buy Progress/Diagnostic events and step composition, but it has never run inside a
deployable and persistence would have to be bolted on either way. Adopt grind only if a second
pipeline actually needs its composition; do not build a third mechanism.

**Credentials are minted server-side and injected into the build workload's `environment` —
no service gets `@Publish`ed for this.** Two credentials: the GitHub clone token (from
`issueRepoToken`, which stays un-`@Publish`ed; the server calls it in-process) and a
short-lived, project-scoped participant token for the sync step. Both expire quickly, are
scoped to the one project being deployed, and are never exposed to or configurable by
application code (they exist only in the build workload, which runs no user code beyond
`bun install` — see open question 5). The CLI needs a non-interactive auth path (token via
env var) alongside the device grant.

**Container contracts.**
- *Runtime image* (`kinotic-app-runtime`): chokidar baked in (the test installs it at runtime;
  production must not), a runner that spawns one `bun` process per
  `packages/microservices/*/src/main.ts` (the workspace glob is the template's convention —
  there is no supervisor in `kinotic-tpl-isomorphic-ts`), each child's stdout/stderr redirected
  to its own file under `/var/log/kinotic` so per-service logs reach Loki with the existing
  labels. On marker change: SIGTERM children with a grace period, `bun install` (lockfile may
  have changed), respawn. The runner process itself must never exit (zombie-box rule).
- *Build workload*: entrypoint script that clones/resets to the head of `develop`, runs
  `bun install --frozen-lockfile`, runs `kinotic sync`, writes the marker, and redirects all of
  its own output to `/var/log/kinotic/build.log` (entrypoint stdout is host-invisible). It is a
  run-to-completion job — reconcile with boxlite's zombie-box behavior (finding #6 in the
  boxlite-test README): the pipeline destroys the build workload after observing completion
  rather than expecting it to exit cleanly.

**Placement.** First deploy: pick the node once (`findAvailableNode`) for the runtime workload,
run the build workload pinned to that node first (via the `@Scope nodeId` path), start the
runtime after the build step succeeds. Subsequent deploys: read the runtime workload's recorded
`nodeId`, pin the build workload there, done. Whether this lands as a `nodeId`-honoring variant
inside `WorkloadOrchestrationService` or direct `VmManagerProxy` calls from the pipeline is an
implementation choice for Phase 4 — but do not silently change `deployWorkload`'s public
contract.

## Phases

**Phase 1 — Wire `kinotic-orchestrator` into `kinotic-server`.** Add the module dependency,
resolve whatever breaks (it has never run inside a deployable; `src/testx/` tests exist but
prove nothing about Spring wiring), gate it with a `kinotic.disable*`-style property consistent
with the established idiom, and verify `WorkloadOrchestrationService` + `VmManagerProxy` are
reachable end-to-end against a real vm-manager (the e2e harness in `kinotic-js/e2e-tests` is
the place). This also answers `Multi-environment architecture.md` open question 3 — update that
doc.

**Phase 2 — `TaskExecution` entity, repository, service, stream.** Index in `V1__init.sql`,
entity + step DTO + enums in `kinotic-domain` `api/model`, repository beside
`WorkloadRepository`, `@Publish` service (reads + per-project `Flux` stream), TS mirror in
`os-api`. No producer yet; tests via the service interface.

**Phase 3 — Trigger.** (a) Provisioner creates the `develop` branch at project creation via the
existing `createBranch`. (b) A deployment trigger in the orchestrator subscribes to the
`push` events republished by `kinotic-github` (verify the event-bus subscription/wildcard
semantics for `evt://github/...` CRIs before building on them — if per-project dynamic
subscription is awkward, promoting `push` to an explicit case in
`DefaultGitHubWebhookEventService` that calls a published orchestrator service is the fallback;
pick one, don't build both). Extract `ref`, filter `refs/heads/develop`, coalesce + serialize
per project, create the `TaskExecution`, invoke the pipeline. Idempotency tests: duplicate
delivery, rapid successive pushes, push during a running deploy.

**Phase 4 — Pipeline + container contracts.** The build/runtime images, the marker-file
contract, credential minting + env injection, the non-interactive CLI auth path, the pinned
build workload, first-deploy bootstrap vs update-in-place, build-workload teardown. The
cross-box marker/restart behavior gets a real-VM test in `vm-manager` (gated on `/dev/kvm`,
like `BoxliteProvider.recovery.test.ts`) — today it is proven only in the untracked
`boxlite-test` harness.

**Phase 5 — UI.** Task list per project + task detail (steps, status, timestamps) fed by the
`TaskExecution` stream, and per-step log tailing via `LogService.tail(step.workloadId)` — which
requires the missing client-side Loki-frame parser in `os-api`. First consumer of the whole log
pipe; expect to shake out bugs there.

Per the house docs rule, each phase updates `website/content/**` for whatever user-visible
behavior it adds (the deployment workflow page `01.apps/07.deployment/01.workflow.md` currently
describes an aspirational flow — Phase 4 is the point where it must be rewritten to match
reality; the template README's GitHub-sync claim gets fixed in Phase 3 or 4, whichever ships
the behavior nearest to it).

## Out of scope

- Deploying the project's UI (handled separately; the build step will eventually also produce
  it, but nothing here should depend on that).
- Production releases, promotion between environments, rollback (see the `releases/<sha>`
  upgrade path above and `Multi-environment architecture.md` Phase 9).
- Preview deployments for feature branches or pull requests.

## Open questions for Navid

1. **Machine identity for `kinotic sync`** — is a server-minted short-lived participant token
   injected as env acceptable, or does this want a first-class service-account concept? This is
   the same security decision that has kept `GitHubProjectRepoService` un-`@Publish`ed.
2. **Failure surface of a failed deploy** — build step fails after a partial `kinotic sync`
   (some entities updated, marker never written): the runtime keeps running old code against
   new definitions. Is "old code + new definitions, task marked FAILED" acceptable for
   develop, or does sync need to become effectively transactional first?
3. **Runtime restart semantics** — SIGTERM + grace period for in-flight requests: how long, and
   is dropping in-flight work acceptable on develop?
4. **Resource sizing** — vcpus/memory for build and runtime workloads: fixed platform constants
   for now, or per-project configuration from day one?
5. **`bun install` runs project-controlled code** (postinstall scripts) inside the build
   workload, which holds the sync token and repo token. Acceptable for develop (it is the
   user's own project), or do install and sync need to be separated?
