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
entity sync + publish                         restarts the process when it changes
write .kinotic/reload  ←──────────────────────┘
```

The sentinel is written **last**, only after the checkout, install, and entity sync all
succeeded, so the supervisor never restarts the microservices into a half-updated tree.
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
| `KINOTIC_SERVER_HOST/PORT/USE_SSL`, `KINOTIC_CLIENT_ID`, `KINOTIC_CLIENT_SECRET`, `KINOTIC_ORGANIZATION_ID` | machine identity and server the CLI connects with; sync is skipped when no credentials are present | — |
| `KINOTIC_CLI_BIN` | overrides the kinotic CLI entry script (development/tests) | resolved from the image install |

Entity sync runs `kinotic sync --publish` over the checkout. The projects have no CI of
their own — this deploy run is their pipeline — so the CLI's generation step recompiles the
entity sources (a project that does not build never reaches the server) and pushes the fresh
definitions and migrations, authenticated by the machine identity in the environment.
`--publish` creates the backing index for each entity the deploy introduces, so a pushed
entity is usable for data operations without anyone publishing it by hand; entities already
published are left alone.

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
