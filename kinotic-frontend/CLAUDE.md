# kinotic-frontend

## Workspace Layout

This is a pnpm workspace:

- `apps/portal` — the Kinotic OS dashboard (organization users). Built into the api-gateway
  `webroot` for docker-compose; deployed to Azure static hosting in production.
- `apps/system` — the platform-operator console (system users). Deployed to VPN-restricted
  static hosting only; never baked into the server jar.
- `packages/common` — shared UI code (`@kinotic-ai/frontend-common`): theme preset, auth page
  shell, session state, utils. Consumed as source via `workspace:*` — no build step.

Shared code moves to `packages/common` only once both apps actually consume it.

## Package Manager

**IMPORTANT:** This project uses **pnpm** for package management and script execution.

- Use `pnpm install` at the workspace root to install dependencies
- Use `pnpm add <package>` in the app/package that needs the dependency
- Use `pnpm <script>` to run scripts (root `build` builds every app; `dev` runs the portal,
  `dev:system` runs the system console)
- **DO NOT** use bun, yarn, or npm

Note: the `kinotic-js/workspace` packages use bun — that does not apply here.
