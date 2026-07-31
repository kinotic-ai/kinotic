# Kinotic Claude Code Plugin

Build [Kinotic](https://kinotic.ai) applications with Claude. The plugin connects
Claude to Kinotic OS through the platform's MCP server and teaches Claude the Kinotic
programming model, so a user can go from nothing to a working application: sign up,
create an Application and Project (Kinotic OS provisions the GitHub repository), and
develop entities, services, and frontends.

> This directory is developed inside the kinotic monorepo and is laid out to become
> the root of the dedicated `kinotic-ai/claude-plugin` repository.

## Installation

Once the dedicated repository exists:

```
/plugin marketplace add kinotic-ai/claude-plugin
/plugin install kinotic@kinotic
```

For local development, add the marketplace from a checkout path instead:

```
/plugin marketplace add /path/to/claude-plugin
/plugin install kinotic@kinotic
```

## Configuration

| Setting | Default | Purpose |
|---|---|---|
| `server_url` | `https://api.kinotic.ai/mcp` | The Kinotic OS MCP endpoint. Point it at a self-hosted or local server (`http://localhost:58503/mcp`) to develop against your own Kinotic OS. |

After installing, run `/mcp`, select `kinotic-os`, and authenticate. The browser opens
the Kinotic OS OAuth flow — sign up from the login page if you have no account, then
approve the consent screen. Claude Code stores and refreshes the token automatically.

## What's inside

| Component | Purpose |
|---|---|
| `kinotic-os` MCP server | Remote streamable-HTTP MCP endpoint exposing Kinotic OS platform tools (application/project creation, project lookup) secured by OAuth 2.1 |
| `create-app` skill | End-to-end onboarding: authenticate, create the Application and Project, handle GitHub linking and repo provisioning, clone, verify the scaffold |
| `entities-and-persistence` skill | Entity classes and decorators, `bun run generate`, repository API, named queries, migrations, multi-tenancy |
| `services` skill | Publishing services with `@Publish`, zones and addressing, service proxies, streaming |
| `frontend` skill | Connecting browser and Node clients, authentication recipes, calling services from the UI |
| `/kinotic:new-app` command | Deterministic entry point that runs the create-app workflow |

MCP tool permissions use names of the form
`mcp__plugin_kinotic_kinotic-os__<tool>`, e.g.
`mcp__plugin_kinotic_kinotic-os__os-api.org.kinotic.os.api.services.ApplicationService.createApplicationIfNotExist`.

## End-to-end test checklist (maintainers)

Requires a running Kinotic OS (`kinotic-server`), a test GitHub org with the Kinotic
GitHub App installable, and a browser.

1. Set the plugin's `server_url` to `http://localhost:58503/mcp`.
2. `/mcp` → `kinotic-os` → complete the OAuth flow, including one pass as a brand-new
   signup.
3. Ask Claude to create an app (or run `/kinotic:new-app TestApp`):
   - Application and Project created; correct ids recorded.
   - With GitHub unlinked: the "GitHub is not linked" error is surfaced with dashboard
     instructions, and the same call succeeds after linking.
   - Repo provisioned from the template; `repoConnectionStatus` is `CONNECTED`.
   - Force a baseline failure (e.g. temporarily revoke contents permission) and verify
     the `INITIALIZATION_FAILED` → `retryRepoInitialization` path.
4. Clone the repo and compare against
   `skills/create-app/references/project-scaffold.md` — fix that reference, not the
   clone, if the template has drifted.
5. `bun install`, `bun run generate` (with a first entity), `bun run type-check`,
   commit, push; confirm Kinotic OS synchronizes the entity definitions.
6. Spot-check skill triggering: "define a kinotic entity for orders" should load
   entities-and-persistence, "publish a service" the services skill, and neither
   should fire on unrelated prompts.
