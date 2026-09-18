---
name: org-dashboard
description: >
  Working on the Org Dashboard Kinotic app: what it shows, how its users sign in as
  organization users through the gateway's organization login route and Kinotic.connect,
  where the UI lives, how to run, type-check and build it, how it deploys by pushing the
  project repository, and how to verify a deployment with the Kinotic OS MCP tools. Use
  when changing this dashboard, adding data to it, touching its sign-in, or deploying it.
---

# Org Dashboard

A Kinotic app with one UI artifact, `packages/ui/web` (Vite + Vue, neon theme). It shows
the signed-in member's organization: its applications with their project counts, and its
members. It defines no entities and runs no microservice; everything it displays comes
from the platform's management services through `@kinotic-ai/management-api`.

The Kinotic plugin's skills (`create-app`, `frontend`, `services`,
`entities-and-persistence`, `deploying`) carry the platform rules. This skill carries what
is specific to this app.

## Users are organization users

The dashboard signs members in at **ORGANIZATION** scope, not the APPLICATION scope the
`frontend` skill's Recipe 1 uses. That is the whole point of the app: an organization
member sees the organization, and the management services it reads
(`Kinotic.applications`, `Kinotic.projects`, `Kinotic.members`, `Kinotic.profile`) answer
only an organization participant.

```ts
// packages/ui/web/src/session.ts
await fetch(apiUrl('/api/auth/org/login'), {            // the ORGANIZATION login route
    method: 'POST', credentials: 'include', body: JSON.stringify({ email, password }) })
const connected = await Kinotic.connect({ server: serverOverrides(), maxConnectionAttempts: 3 })
if (!isOrganizationParticipant(connected.participant)) { // admits organization scope only
    await logout()
    throw new Error('This dashboard admits organization users only')
}
```

Consequences:

- Keep `/api/auth/org/login`. `/api/auth/app/:orgId/:appId/login` authenticates an
  application's own users, who cannot call the management services the dashboard reads.
- The organization id is read off the connected participant (`participant.organizationId`),
  never configured, so one build serves any organization.
- Email and password only. The gateway's social login callback returns the browser to the
  Kinotic OS portal, not to the site that started it, so social buttons would strand the user.
- A member who cannot sign in here is not a member of the organization. They are invited
  from the portal's Members page, not created by this app.

## Layout

```
.config/kinotic.config.ts      Project config; entitiesPaths is [] because there are no entities
.mcp.json                      The Kinotic OS MCP server, for Claude Code opened on this repo
packages/ui/web/
  src/main.ts                  Installs ManagementApiPlugin, mounts App
  src/server.ts                serverOverrides() / apiUrl() from the VITE_KINOTIC_* build variables
  src/session.ts               login / restore / logout and the organization-scope guard
  src/App.vue                  restore() on load, then LoginView or DashboardView
  src/components/              LoginView, DashboardView, StatTile
  src/style.css                The neon theme: tokens on :root, panel, button, input, chip, table
  vite.config.ts               Dev proxy of /api and /v1 to localhost:58503
```

Adding a panel to the dashboard means calling another `Kinotic.<service>` in
`DashboardView.vue`'s `load()`, in the `Promise.all`, and rendering it in a `.panel`
section. Use the theme classes in `style.css` rather than new colors.

## Working loop

```bash
bun install
bun run dev          # http://localhost:5180, proxying to a kinotic-server on localhost:58503
bun run type-check   # vue-tsc
bun run build        # type-check, then vite build -> packages/ui/web/dist/index.html
```

`bun run generate` runs but has nothing to generate. In development the page and the
server share one origin through the Vite proxy, so `VITE_KINOTIC_*` stay unset and the
client resolves the page's own location. A deployment sets the three variables at build time.

## Deploying

Pushing the project repository's default branch is the deployment (`deploying` skill).
This app is set up the way the `create-app` skill sets up any Kinotic app, with the
Kinotic OS MCP tools resolved by **title** from the tool listing, never by name:

1. `Application Service Create Application If Not Exist` with
   `{"name": "Org Dashboard", "description": "...", "tenantPerUser": false}`. The
   dashboard stores no per-user data, so `tenantPerUser` is `false`.
2. `Project Service Create Project If Not Exist` with the `main` project
   (`"id": "<application id>-main"`, `"sourceOfTruth": "TYPESCRIPT"`). Kinotic OS
   provisions the GitHub repository and renders the template into it.
3. Clone the provisioned repository and add `packages/ui/web`, `.mcp.json` and `.claude`
   from this directory. The provisioned root already lists `packages/ui/*` in its
   workspaces and pins `@kinotic-ai/core` and `@kinotic-ai/management-api` in its catalog,
   which is all the UI package references. Keep the provisioned `.config/kinotic.config.ts`;
   the one in this directory holds placeholder ids.
4. `bun install`, `bun run type-check`, commit, push.
5. `Project Service Find Deployment` with `{"projectId": "<project id>"}` until
   `status.type` is `RUNNING` on the pushed commit. The UI publishes at
   `https://<org>-<app>-web.<sites domain>`, listed on the project's Deployment page.

## Not available yet

- **App-level MCP tools.** The platform serves `@McpTool` functions of its own Java
  services through `POST /mcp`; a TypeScript `@Publish` service registers no tool
  metadata, so this app cannot expose tools of its own. The `kinotic-os` server in
  `.mcp.json` is the platform's tools, which an organization user's approval scopes to
  their organization.
