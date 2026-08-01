# Kinotic OS MCP tool contracts

The kinotic-os server is a stateless streamable-HTTP MCP endpoint at `POST <server_url>`
secured by OAuth 2.1. Behavior common to every tool:

- **Results are raw JSON in a single text content block.** The tool result contains one
  `text` item whose text is the JSON serialization of the service's return value — parse
  it. An empty list result is the literal text `[]`.
- **Unknown argument keys fail the call** with `isError: true`, not a schema rejection.
  Send exactly the argument names documented below.
- **Errors arrive as `isError: true`** with the exception message as text content.
  Match on the message substrings documented below.
- Authenticated users act as their Kinotic **organization**, so the tools below operate
  on that organization's applications and projects.
- Both services expose their **full CRUD surface** as additional tools (`save`,
  `create`, `findById`, `findAll`, `search`, `count`, `deleteById`, …) beyond those
  documented below. Stick to the documented tools for the onboarding workflow, and
  never call a destructive tool such as `deleteById` unless the user explicitly asks
  for that exact operation.

## ApplicationService

### `os-api.org.kinotic.os.api.services.ApplicationService.createApplicationIfNotExist`

Creates an application, or returns the existing one whose id matches the slugified name.
Idempotent.

Arguments:

```json
{ "name": "Inventory App", "description": "Tracks warehouse inventory" }
```

`name` must start with a letter and contain only letters, numbers, periods, underscores,
or dashes.

Result (Application):

```json
{
  "id": "inventory-app",
  "organizationId": "acme",
  "name": "Inventory App",
  "description": "Tracks warehouse inventory",
  "tenantPerUser": false,
  "updated": 1753747200000
}
```

`id` is the server-minted slug of `name` (lowercase letters, digits, interior dashes).
`organizationId` is derived from the authenticated user — pass both into project creation.

### `os-api.org.kinotic.os.api.services.ApplicationService.getOidcConfigurations`

Read-only. Returns the enabled OIDC configurations registered on an application (used by
the frontend skill when wiring login). Returns `[]` when the application is not found or
has none.

Arguments:

```json
{ "applicationId": "inventory-app" }
```

## ProjectService

### `os-api.org.kinotic.os.api.services.ProjectService.createProjectIfNotExist`

Creates a project and provisions its GitHub repository from the Kinotic template through
the organization's GitHub App installation. Returns the existing project unchanged if
one with the same id exists. Idempotent.

The single argument key is exactly `project`:

```json
{
  "project": {
    "applicationId": "inventory-app",
    "organizationId": "acme",
    "name": "Inventory App",
    "description": "Tracks warehouse inventory",
    "repoPrivate": true,
    "sourceOfTruth": "TYPESCRIPT"
  }
}
```

- `applicationId` and `name` are required; the project id is derived as
  `<applicationId>-<slugified name>` when not set (e.g. `inventory-app-inventory-app`).
- `organizationId` must match the caller's organization.
- `sourceOfTruth` accepts only `"TYPESCRIPT"` today.
- `repoPrivate` controls the GitHub repository visibility at creation.

Result (Project) — fields beyond the input:

```json
{
  "id": "inventory-app-inventory-app",
  "repoFullName": "acme-gh-org/inventory-app",
  "repoId": 123456789,
  "repoDefaultBranch": "main",
  "repoConnectionStatus": "CONNECTED",
  "updated": 1753747200000
}
```

`repoConnectionStatus` values:

| Status | Meaning | Action |
|---|---|---|
| `CONNECTED` | Repo provisioned and baseline committed | Proceed |
| `INITIALIZATION_FAILED` | Repo exists but the baseline commit failed | Call `retryRepoInitialization` |
| `DISCONNECTED` | GitHub revoked the platform's access to the repo | User must re-link in the dashboard |

Errors:

- `"GitHub is not linked for this organization. Link GitHub before creating a project."`
  — the organization has no GitHub App installation. The user links GitHub in the
  Kinotic OS dashboard organization settings, then the same call is re-run.

### `os-api.org.kinotic.os.api.services.ProjectService.retryRepoInitialization`

Re-runs repository initialization for a project left in `INITIALIZATION_FAILED`.
Succeeds with the project marked `CONNECTED` once the baseline is committed.

Arguments:

```json
{ "projectId": "inventory-app-inventory-app" }
```

Errors:

- `"Project for id <projectId> does not exist"`
- `"Project <projectId> is not awaiting initialization retry (status <status>)"` — the
  project is not in `INITIALIZATION_FAILED`; never call this tool speculatively.

### `os-api.org.kinotic.os.api.services.ProjectService.findByRepoFullName`

Read-only. Looks up projects in the caller's organization whose backing GitHub repo has
the given `owner/repo` full name. Returns `[]` when none match.

Arguments:

```json
{ "repoFullName": "acme-gh-org/inventory-app" }
```
