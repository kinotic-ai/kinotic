# kinotic-js/workspace

## Package Manager

**IMPORTANT:** This project uses **Bun** exclusively for package management and script execution.

- Use `bun install` to install dependencies
- Use `bun add <package>` to add dependencies
- Use `bun run <script>` to run scripts
- **DO NOT** use pnpm, yarn, or npm

## Versioning: peer dependencies between workspace packages

A workspace package that another workspace package augments at runtime (`core`, which `management-api` and `system-api` extend; `management-api`, which `system-api` extends) is declared as a **peerDependency** with `workspace:^`, plus a `workspace:*` devDependency for local builds. `bun publish` rewrites both from the versions `bun install` recorded in `bun.lock`: the peer becomes `^<current version of that package>`, the devDependency its exact version. So the floor a published consumer resolves against always names the version the package was built against, with nothing to edit by hand.

What still moves by hand is the dependent's own `version`: a package is published only when its version is not on the registry yet, so when a package starts using a new API of one it augments, bump its `version` in the same change, or the publish that carries the new floor never happens.

## Kinotic Service Registration

**IMPORTANT:** Any service class that needs to be called remotely via the Kinotic event bus **must** use the `@Publish` decorator from `@kinotic-ai/core`. Without `@Publish`, the service will not be registered with the `ServiceRegistry` and will not be accessible through service proxies.

```typescript
import { Publish } from '@kinotic-ai/core'

@Publish('org.kinotic.your.namespace')
export class YourService {
    // methods callable via IServiceProxy.invoke()
}
```

The `@Publish` decorator takes an optional `namespace` and optional `name` (defaults to the class name). Related decorators include `@Scope` on a getter or method (for routing to specific service instances), `@ScopeOptional` on a method of a scoped service that any instance may answer (the service then also listens on its shared unscoped address, where only the annotated methods may be invoked), `@Version` (for semantic versioning), and `@Zone` (declares the zone a service is addressable in, appended to `Kinotic.zonePrefix`; without a declaration, `Kinotic.defaultZone` from the project package.json `kinotic.zone` field applies).


## Vitest and TC39 decorators

The root `package.json` pins `"overrides": { "vite": "^7.3.5" }`. Vitest 4 otherwise
resolves Vite 8, whose rolldown/oxc transform passes TC39 decorator syntax through
untransformed (no error, raw `@` reaches Node as a SyntaxError). Vite 7 transforms TS
with esbuild, which lowers stage-3 decorators correctly. Remove the override only once
oxc lowers proposal decorators (tracked by rolldown/oxc upstream).
