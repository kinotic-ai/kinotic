# Expected Kinotic project scaffold

The repository provisioned by Kinotic OS is rendered from the Kinotic project template
(a Bun workspace mono repo). **The cloned repository is the source of truth** — if it
differs from this document, trust the clone, report the difference to the user, and do
not "fix" the clone to match this document.

## Layout

```
<repo root>/
├── package.json              # Bun workspace root
├── tsconfig.base.json
├── bunup.config.ts
├── .config/
│   └── kinotic.config.ts     # Kinotic project configuration (see below)
├── migrations/               # V<N>__<description>.sql migration files (may be absent until first used)
└── packages/
    ├── domain/               # entity model + generated repositories
    │   ├── package.json
    │   ├── tsconfig.json
    │   ├── model/            # @Entity classes go here
    │   └── repositories/     # kinotic sync/generate writes repository classes here
    ├── microservices/        # @Publish service classes
    └── ui/                   # frontend packages
```

## Root `package.json`

```json
{
    "name": "<project name>",
    "private": true,
    "scripts": {
        "build": "bunup",
        "dev": "bunup --watch",
        "type-check": "bun run --filter '*' type-check"
    },
    "catalog": {
        "@kinotic-ai/core": "<pinned version>",
        "@kinotic-ai/persistence": "<pinned version>"
    },
    "workspaces": ["packages/*"],
    "type": "module"
}
```

Workspace packages reference the catalog (`"@kinotic-ai/core": "catalog:"`) so the
Kinotic SDK version is pinned once at the root.

## `.config/kinotic.config.ts`

A TypeScript module default-exporting a `KinoticProjectConfig`:

```typescript
import type { KinoticProjectConfig } from '@kinotic-ai/os-api'

const config: KinoticProjectConfig = {
  organization: "<organization id>",
  application: "<application id>",
  entitiesPaths: [
    {
      path: "packages/domain/model",
      repositoryPath: "packages/domain/repository",
      mirrorFolderStructure: true
    }
  ],
  fileExtensionForImports: ".js",
  validate: false
}

export default config
```

- `organization` and `application` must match the Application created through the MCP
  tools (Step 1 of the workflow). If they differ, `kinotic sync` will target the wrong
  application — surface this to the user before syncing.
- `entitiesPaths[].path` is where `@Entity` classes are discovered;
  `repositoryPath` is where repository classes are generated. Trust the values in the
  cloned config over the ones shown here — the directory name for repositories has
  varied between template revisions (`repository` vs `repositories`).

## Verification checklist

1. `package.json` at the root declares `"workspaces": ["packages/*"]` and the
   `@kinotic-ai/*` catalog entries.
2. `.config/kinotic.config.ts` exists, default-exports a config, and its
   `organization`/`application` match the created Application.
3. The directories named by `entitiesPaths` exist (create the repository output
   directory if the template left it empty — empty directories don't survive git).
4. `bun install` succeeds.
5. `bun run type-check` succeeds.
