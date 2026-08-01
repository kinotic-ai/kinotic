# Expected Kinotic project scaffold

The repository provisioned by Kinotic OS is rendered from the Kinotic project template
(a Bun workspace mono repo). **The cloned repository is the source of truth** — if it
differs from this document, trust the clone, report the difference to the user, and do
not "fix" the clone to match this document.

## Layout

```
<repo root>/
├── README.md
├── package.json              # Bun workspace root
├── tsconfig.json             # `bun run generate` reads compilerOptions from here
├── tsconfig.base.json
├── bunup.config.ts           # bunup workspace definition (packages are registered here)
├── .config/
│   ├── kinotic.config.ts     # Kinotic project configuration (see below)
│   └── c3/                   # generated entity/query schemas (written by `bun run generate`)
├── .kinotic/                 # local incremental-generation cache (gitignored — never commit)
├── migrations/               # V<N>__<description>.sql migration files (may be absent until first used)
└── packages/
    ├── domain/               # entity model + generated repositories
    │   ├── package.json
    │   ├── tsconfig.json
    │   ├── index.ts          # package entry — export entities/repositories here
    │   ├── model/            # @Entity classes go here
    │   └── repositories/     # `bun run generate` writes repository classes here
    ├── microservices/        # each microservice is its own nested workspace package
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
        "generate": "kinotic generate",
        "type-check": "bun run --filter '*' type-check"
    },
    "devDependencies": {
        "@kinotic-ai/kinotic-cli": "<pinned version>",
        "@kinotic-ai/os-api": "<pinned version>"
    },
    "catalog": {
        "@kinotic-ai/core": "<pinned version>",
        "@kinotic-ai/persistence": "<pinned version>"
    },
    "workspaces": ["packages/*", "packages/microservices/*", "packages/ui/*"],
    "type": "module"
}
```

Workspace packages reference the catalog (`"@kinotic-ai/core": "catalog:"`) so the
Kinotic SDK version is pinned once at the root.

The Kinotic CLI ships as a project dependency with a `generate` script wired in
`package.json` — repository classes are generated with `bun run generate`, entirely
locally. Server synchronization is handled by Kinotic OS from the connected GitHub
repository; the globally-installed CLI (`kinotic login`, `kinotic sync`) is a
human-operator tool that Claude does not use.

## `.config/kinotic.config.ts`

A TypeScript module default-exporting a `KinoticProjectConfig`:

```typescript
import type { KinoticProjectConfig } from '@kinotic-ai/os-api'

const config: KinoticProjectConfig = {
  organizationId: "<organization id>",
  applicationId: "<application id>",
  entitiesPaths: [
    {
      path: "packages/domain/model",
      repositoryPath: "packages/domain/repositories",
      mirrorFolderStructure: true
    }
  ],
  fileExtensionForImports: ".js",
  validate: false
}

export default config
```

- `organizationId` and `applicationId` must match the Application created through the
  MCP tools (Step 1 of the workflow). If they differ, synchronization will target the
  wrong application — surface this to the user before pushing.
- `entitiesPaths[].path` is where `@Entity` classes are discovered;
  `repositoryPath` is where repository classes are generated. Trust the values in the
  cloned config over the ones shown here.

## Adding a microservice or UI package

Each microservice or UI is its own workspace package nested under
`packages/microservices/` or `packages/ui/` (e.g. `packages/microservices/todo-service`)
— the root workspace globs cover exactly that depth. A minimal service package:

```json
{
    "name": "@<project slug>/todo-service",
    "version": "0.1.0",
    "type": "module",
    "scripts": {
        "type-check": "tsc --noEmit"
    },
    "dependencies": {
        "@<project slug>/domain": "workspace:*",
        "@kinotic-ai/core": "catalog:"
    }
}
```

```jsonc
// tsconfig.json — three levels up to the base config
{
    "extends": "../../../tsconfig.base.json",
    "files": [],
    "include": ["**/*"],
    "exclude": ["node_modules", "dist"]
}
```

Register the package in `bunup.config.ts`, and run `bun install` after creating it.
Packages importing the domain package resolve its types from the built `dist/`
output, so run `bun run build` after changing domain code or its export barrel —
otherwise their `type-check` fails with TS7016 on the domain import.

## Verification checklist

1. `package.json` at the root declares the workspace globs shown above and the
   `@kinotic-ai/*` catalog entries.
2. `.config/kinotic.config.ts` exists, default-exports a config, and its
   `organizationId`/`applicationId` match the created Application.
3. The directories named by `entitiesPaths` exist (create the repository output
   directory if the template left it empty — empty directories don't survive git).
4. `bun install` succeeds.
5. `bun run type-check` succeeds.
