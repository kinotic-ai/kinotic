# @kinotic-ai/workload-runner

Entrypoints for the two workloads that deploy and run a customer project on a node. Baked
into the `workload-runner` OCI image (see the `Dockerfile`), never published to npm.

## How a project runs on a node

Both workloads share one host directory — the project checkout — which the node creates
under its workload data directory:

```
sync workload (one-shot, per push)            runtime workload (long-lived)
──────────────────────────────────            ─────────────────────────────
bun src/sync.ts                               bun src/supervise.ts   (image default)
mounts <checkout> read-write                  mounts <checkout> read-only
fetch + checkout GIT_REF                      runs the project's microservice entry
bun install                                   polls .kinotic/reload for changes
find artifacts                                restarts the process when it changes
entity sync + publish
build the UIs
report artifacts to the server
write .kinotic/reload  ←──────────────────────┘
```

The sentinel is written **last**, only after the checkout, install, entity sync, UI builds
and artifact report all succeeded, so the supervisor never restarts the microservices into
a half-updated tree.
It is polled (`fs.watchFile`) rather than watched because the two workloads are separate
micro VMs sharing a host mount, and inotify events do not cross the VM boundary.

## Environment

`sync.ts` — one-shot, exits 0 on success:

| Variable | Meaning | Default |
|---|---|---|
| `GIT_CLONE_URL` | https URL of the repository | required |
| `GIT_REF` | commit sha or branch to deploy | required |
| `GIT_TOKEN` | token authorizing the fetch; omit for a public repository | — |
| `KINOTIC_WORKSPACE_DIR` | the shared checkout directory | `/workspace` |
| `KINOTIC_PROJECT_ID` | the project the checkout belongs to, named in the artifact report | required with credentials |
| `KINOTIC_UI_SERVER_URL` | the address a browser reaches the platform on, handed to every UI build | — |
| `KINOTIC_SERVER_HOST/PORT/USE_SSL`, `KINOTIC_CLIENT_ID`, `KINOTIC_CLIENT_SECRET`, `KINOTIC_ORGANIZATION_ID` | machine identity and server the CLI and the artifact report connect with; both are skipped when no credentials are present | — |
| `KINOTIC_CLI_BIN` | overrides the kinotic CLI entry script (development/tests) | resolved from the image install |

Entity sync runs `kinotic sync --publish` over the checkout. The projects have no CI of
their own — this deploy run is their pipeline — so the CLI's generation step recompiles the
entity sources (a project that does not build never reaches the server) and pushes the fresh
definitions and migrations, authenticated by the machine identity in the environment.
`--publish` creates the backing index for each entity the deploy introduces, so a pushed
entity is usable for data operations without anyone publishing it by hand; entities already
published are left alone.

Artifact discovery (`src/artifacts.ts`) runs over the checkout before the entity sync: a
directory directly under `packages/microservices` holding a `package.json` is a microservice,
one directly under `packages/ui` whose `package.json` declares a `build` script is a UI, and
each is identified by the unscoped `name` in its `package.json`, which must be lowercase
letters, digits, and interior dashes. A missing or invalid name, or two packages of one kind
sharing a name, fails the run naming the package. The result is reported to the server
through `ProjectArtifactService.recordArtifacts`, authenticated as the sync machine, so
the deployment run can bind it once this workload exits.

Every UI artifact is then built in place with `bun run build`, handed `KINOTIC_UI_BASE_PATH`
(`/<commit>/`, so its assets are served under the commit and cached forever),
`KINOTIC_UI_COMMIT` and `KINOTIC_UI_SERVER_URL`. A build that leaves no `dist/index.html`
fails the run naming the UI.

The git token travels as a per-invocation `http.extraheader`, never written to
`.git/config` or embedded in the remote URL — the checkout is a shared host directory and
must not hold a credential.

`supervise.ts` — long-lived:

| Variable | Meaning | Default |
|---|---|---|
| `KINOTIC_APP_DIR` | the read-only checkout mount | `/app` |
| `KINOTIC_APP_ENTRY` | entry file relative to the checkout | `packages/microservices/main/src/main.ts` |
| `KINOTIC_RELOAD_POLL_MS` | sentinel poll interval | `1000` |

A microservice process that dies is respawned with a backoff doubling from 1s to 30s; a
sentinel change restarts it immediately.

## Logs

Both entrypoints write everything — their own messages and the output of the processes they
run — to stdout and stderr. A node whose VM runtime captures no stdout (`BOXLITE`) mounts a
log directory into the guest and names it in the environment; the runner then also writes
into a size-rotated file there, which is what that node ships.

| Variable | Meaning | Default |
|---|---|---|
| `KINOTIC_LOG_DIR` | directory to write `workload.log` into | unset: stdout only |
| `KINOTIC_LOG_MAX_SIZE_MB` | size at which `workload.log` rotates | required with the directory |
| `KINOTIC_LOG_MAX_FILES` | rotated files (`workload.log.1` …) kept beside it | required with the directory |
