# Org Dashboard

A Kinotic app that shows an organization to its own members: the applications it owns, how
many projects each carries, and who belongs to the organization, in a neon theme. It is one
UI, built the way any Kinotic project builds a frontend, and it reads the platform's
management services rather than entities of its own, so the same build serves whichever
organization the signed-in user belongs to.

## Layout

```
.config/kinotic.config.ts    Project config (organization, application, project)
.mcp.json                    The Kinotic OS MCP server, for Claude Code opened on this repository
.claude/skills/org-dashboard The app's skill: how it signs users in, how to change and deploy it
packages/ui/web              The dashboard: a Vite + Vue UI, deployed as the UI artifact `web`
```

The repository is a Bun workspace shaped like the one Kinotic OS provisions for a project,
with `packages/ui/*` as its only workspace members: the dashboard defines no entities and
runs no microservice.

## How users sign in

Members sign in as **organization users**. The login view posts the email and password to the
gateway's organization login route, `POST /api/auth/org/login`, which verifies them and
establishes the browser session (`204` + `Set-Cookie`). The dashboard then calls
`Kinotic.connect()` with no credentials: in a browser the default credential resolution
supplies none, and the session cookie authenticates the WebSocket upgrade. The connection is
admitted only when the participant it authenticated is organization-scoped
(`isOrganizationParticipant`), and the organization id the dashboard displays is read off
that participant.

```ts
// packages/ui/web/src/session.ts
await fetch(apiUrl('/api/auth/org/login'), { method: 'POST', credentials: 'include', body: JSON.stringify({ email, password }) })
const connected = await Kinotic.connect({ server: serverOverrides() })
if (!isOrganizationParticipant(connected.participant)) { /* rejected */ }
```

A returning visit probes `GET /api/auth/me` and reconnects when the cookie still
authenticates; **Sign out** posts `/api/auth/logout` and closes the connection. Social
sign-in is not offered: the gateway's social login callback returns the browser to the
Kinotic OS portal, not to the site that started it.

## Running locally

Requires [Bun](https://bun.sh) and a kinotic-server reachable at `localhost:58503` (the
docker-compose stack in `deployment/docker-compose/`, or a server started from this
repository).

```bash
bun install
bun run dev
```

The dev server on `http://localhost:5180` proxies `/api` and `/v1` to the local server, so
the page and the API share one origin and the session cookie is first-party. Sign in with
any organization member's email and password.

```bash
bun run type-check   # vue-tsc over the UI
bun run build        # type-check, then vite build into packages/ui/web/dist
```

## Deploying

Kinotic deploys a project by [push to deploy](https://kinotic.ai/docs/apps/deployment/push-to-deploy):
every push to the default branch of the project's provisioned repository builds the UIs it
finds under `packages/ui` and publishes each to its own site. The app is created the way the
Kinotic Claude plugin's `create-app` skill creates any Kinotic app, through the Kinotic OS
MCP server; tool names are opaque hashes, so tools are resolved by **title** from the tool
listing:

1. `Application Service Create Application If Not Exist` with
   `{"name": "Org Dashboard", "description": "...", "tenantPerUser": false}`. Record the
   returned `id` and `organizationId`.
2. `Project Service Create Project If Not Exist` with
   `{"project": {"id": "<application id>-main", "applicationId": "<application id>",
   "organizationId": "<organizationId>", "name": "Org Dashboard", "description": "...",
   "repoPrivate": true, "sourceOfTruth": "TYPESCRIPT"}}`. Kinotic OS provisions the
   project's GitHub repository from its template, with a `.config/kinotic.config.ts`
   carrying the project's ids. Record `repoFullName`.
3. Clone that repository and add `packages/ui/web`, `.mcp.json` and `.claude` from this
   directory. The provisioned root already lists `packages/ui/*` in its workspaces and pins
   `@kinotic-ai/core` and `@kinotic-ai/management-api` in its catalog, which is everything
   the UI package references. Keep the provisioned `.config/kinotic.config.ts`; the one in
   this directory holds placeholder ids for a checkout outside a provisioned repository.
4. `bun install`, `bun run type-check`, commit and push. The deployment builds
   `packages/ui/web` with `VITE_KINOTIC_HOST`, `VITE_KINOTIC_PORT` and
   `VITE_KINOTIC_USE_SSL` set to the platform's address and publishes it at
   `https://<org>-<app>-web.<sites domain>`.
5. `Project Service Find Deployment` with `{"projectId": "<project id>"}` until
   `status.type` is `RUNNING` on the pushed commit. The project's Deployment page lists
   the site.

The published site and the API share one site in production, so the `SameSite=Lax` session
cookie the login sets is sent on the dashboard's requests. A deployment whose sites and API
live on unrelated domains needs `kinotic.apiGateway.sessionCookieSameSite: NONE` on the
server, and the site's origin admitted by the gateway's CORS pattern.

## Working on it with Claude Code

`.mcp.json` registers the Kinotic OS MCP server (`kinotic-os`, the same dev server the
[Kinotic Claude plugin](https://github.com/kinotic-ai/claude-plugin) targets), so Claude Code
opened on this repository has the platform's tools once `/mcp` → `kinotic-os` is
authenticated as an organization member. A different Kinotic OS is added alongside with
`claude mcp add -s user --transport http kinotic-os-test <url>/mcp`.

`.claude/skills/org-dashboard/SKILL.md` is the app's own skill: the organization-scope
sign-in contract, the layout, the working loop and the deployment steps above. The plugin's
skills carry the platform rules it builds on.

The platform serves MCP tools only for its own services today; a TypeScript `@Publish`
service registers no tool metadata, so the dashboard exposes no tools of its own.
