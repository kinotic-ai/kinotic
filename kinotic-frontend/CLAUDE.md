# kinotic-frontend

## Workspace Layout

This is a pnpm workspace:

- `apps/portal` — the Kinotic OS dashboard (organization users). Built into the management server's
  `webroot` for docker-compose; deployed to Azure static hosting in production.
- `apps/system` — the system console (system users). Deployed to VPN-restricted
  static hosting only; never baked into the server jar.
- `packages/common` — shared UI code (`@kinotic-ai/frontend-common`): theme preset, auth page
  shell, session state, utils. The apps consume it as source via `workspace:*`, with no build step;
  apps outside this repository install the published build from npm.

Shared code moves to `packages/common` only once both apps actually consume it.

Import `@kinotic-ai/frontend-common` statically, never with `import()`: its index chunk is in every
app's startup graph, so a dynamic import loads nothing later and makes that chunk import every chunk
holding one of its exports, which can close a cycle that leaves the page blank. Both apps' production
builds fail on a dynamic import of it or its subpaths and on any import cycle between output chunks
(`packages/common/vite/bundleGuards.ts`).

## frontend-common has two entries

- `@kinotic-ai/frontend-common` (`src/index.ts`) — the theme, `createKinoticApp`, session state, and the
  generic components and utilities. Nothing reachable from it imports `@kinotic-ai/management-api` or
  reads a management service off `Kinotic` (`Kinotic.jobMonitoring`, `Kinotic.logs`, ...).
- `@kinotic-ai/frontend-common/management` (`src/management.ts`) — everything that does: job runs,
  telemetry, workload logs, machines, connected apps, the OAuth consent page. It also registers the
  ECharts modules every chart renders with.

Export new code from the entry that matches its imports, so an app that wants only the generic entry
never bundles management code or needs `@kinotic-ai/management-api` installed (it is an optional peer).
A module-level value built from a management-api import (`TASK_VIEWS`, keyed by `TaskViews`) cannot be
tree-shaken, so a generic module that pulls one in costs every consumer the components it references.

## Publishing frontend-common

The workspace's `package.json` points `main` and `exports` at `src`; `publishConfig` replaces them with
`dist` in the published `package.json`, and `pnpm publish` turns the `catalog:` versions into ranges.
`pnpm --filter @kinotic-ai/frontend-common build` (also part of the root `build`) runs the library
build in `packages/common/vite.config.ts` and emits the `.d.ts` files with
`vue-tsc -p tsconfig.build.json`. The build:

- externalizes every dependency and peer dependency, so the consumer's single copy of each is used
- writes every component style and `src/styles` into `dist/style.css`, which consumers import
- copies the images to `dist/assets` and imports them from there, rather than inlining them
- bundles each Lucide icon that `lucideIcons.ts` loads by name into `dist/icons`, since Lucide removes
  icon files between minor versions
- keeps `import.meta.env.VITE_KINOTIC_*` for the consumer's build to supply

To release, bump `version` in `packages/common/package.json` and run `pnpm run publish` at the
workspace root (`pnpm publish` would be pnpm's own command): it installs from the lockfile, skips every
package whose version is already on the registry, builds the rest and publishes them, a `-beta.`
version under the `beta` dist-tag. `packages/common/README.md` is the consumer's guide.

## Add and edit forms open in a right-hand drawer

A form that adds or edits something (an application, a project, a member, a sign-in provider)
opens in `FormDrawer` from `@kinotic-ai/frontend-common`, the panel that slides in from the right,
never in a modal dialog. Put the fields in a `<form id="…" @submit.prevent="…">` in the default slot
and the buttons in the `footer` slot, with the submit button naming the form (`form="…"`);
`ApplicationSidebar.vue` and `OidcConfigurationDrawer.vue` in the portal are examples. A row's
details open in `SteppingDrawer`, also on the right. A modal is for what is not a form: a
confirmation (`useConfirm`), a value shown once (`MachineSecretDialog`), or a log viewer.

## Package Manager

**IMPORTANT:** This project uses **pnpm** for package management and script execution.

- Use `pnpm install` at the workspace root to install dependencies
- Use `pnpm add <package>` in the app/package that needs the dependency
- Use `pnpm <script>` to run scripts (root `build` builds every app and frontend-common's library
  build; `dev` runs the portal, `dev:system` runs the system console; `pnpm run publish` releases
  frontend-common)
- **DO NOT** use bun, yarn, or npm

Note: the `kinotic-js/workspace` packages use bun — that does not apply here.
