# kinotic-js

## Bumping the published packages

The `@kinotic-ai/*` packages are published from `workspace/` and consumed by projects that
pin them from the registry. A bump is done so that every consumer resolves exactly one
version of each `@kinotic-ai` package: `management-api` and `system-api` augment `core`'s
`Kinotic` singleton, so two versions of them in one dependency graph give the consumer
different `Kinotic.*` service types.

### The graph

Each published package depends on the ones before it:

```
idl → core → persistence → management-api → system-api → vm-manager
                                          ↘ kinotic-cli (pins core, idl, management-api, persistence, spawn)
```

Inside `workspace/`, `bun publish` writes each `workspace:` dependency as the version
`bun.lock` records (see `workspace/CLAUDE.md`), and a package is published only when its own
`version` is not on the registry yet. So bumping one package means bumping the `version` of
**every published package downstream of it**, in the same change. An unbumped dependent
stays on the registry with its old pins, and any consumer installing both gets two
versions. `persistence` depends on `idl` and `core` but not on `management-api`, so it is
easy to miss.

### The consumers that pin them

Update every pin to the new versions in the same change as the bump:

| Consumer | Where the pins live | Lockfile |
|---|---|---|
| `kinotic-cli` | `kinotic-cli/package.json`, exact versions (it is published too: bump its `version`) | `kinotic-cli/pnpm-lock.yaml` |
| Portal and system console | the `catalog:` in `../kinotic-frontend/pnpm-workspace.yaml` | `../kinotic-frontend/pnpm-lock.yaml` |
| e2e tests | `e2e-tests/package.json`, including the `kinotic-cli` devDependency | `e2e-tests/pnpm-lock.yaml` |
| Workload runner | `workload-runner/package.json`, including `kinotic-cli` | `workload-runner/bun.lock`, installed `--frozen-lockfile` by its Dockerfile |
| vm-manager's test fixture | `workspace/packages/vm-manager/test/fixtures/echo-project/package.json` | none |

A consumer that pins `kinotic-cli` gets the CLI's exact pins as well, so a CLI left behind
brings its own `management-api` and `core` into that graph. `load-generator` is out of
date, pins old majors, and is left out of bumps.

Find every pin before and after the edit:

```bash
git grep -nE '@kinotic-ai/[a-z-]+.{0,6}[0-9]+\.[0-9]+\.[0-9]+' -- ':/*package.json' ':/*pnpm-workspace.yaml' ':(top,exclude)kinotic-js/load-generator'
```

A new first-party package a pnpm consumer starts depending on goes into that consumer's
`minimumReleaseAgeExclude` in its `pnpm-workspace.yaml`, or a version published within the
last day is refused.

### Publishing and lockfiles

1. Publish in the order of the graph, so each package's dependencies exist when it is
   installed: `idl`, `core`, `persistence`, `management-api`, `system-api`, `vm-manager`,
   then `kinotic-cli`.
2. Refresh each lockfile in the table only after its packages are on the registry: CI and
   the workload runner's image install with frozen lockfiles, so a pin the lockfile does
   not resolve fails the build. Merge the change after the lockfiles are refreshed, since
   a push to `develop` that touches `workload-runner/` rebuilds its image. Each pnpm
   consumer pins pnpm with `packageManager` in its `package.json`; `pnpm` run by hand
   switches to that version and the Gradle builds read it, so refresh with plain
   `pnpm install` in the consumer's directory and CI's frozen install reads what it wrote.
3. Check, from `kinotic-js/`, that each lockfile holds one version of every `@kinotic-ai` package:

   ```bash
   for f in kinotic-cli/pnpm-lock.yaml e2e-tests/pnpm-lock.yaml workload-runner/bun.lock ../kinotic-frontend/pnpm-lock.yaml; do
     echo "== $f"; grep -oE '@kinotic-ai/[a-z-]+@[0-9]+\.[0-9]+\.[0-9]+(-[a-z]+\.[0-9]+)?' "$f" | sort -u
   done
   ```

   A package listed twice is a duplicate, or an entry the refresh left behind: an importer
   can keep `version: 5.0.0-beta.5` under `specifier: ^5.0.0-beta.6`, which
   `pnpm install --frozen-lockfile` then refuses ("The importer resolution is broken").
   Run the frozen install of each lockfile before pushing, as CI and the workload runner's
   Dockerfile will (`bun install --production --frozen-lockfile` for the runner).

A lockfile refreshed with a different pnpm release than the one that wrote it can rewrite
the peer suffixes of unrelated entries (`(supports-color@8.1.1)`), turning a version bump
into a diff of the whole file. Keep a bump's lockfile diff to the `@kinotic-ai` entries.
