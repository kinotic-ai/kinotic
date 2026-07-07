# kinotic-js/workspace

## Package Manager

**IMPORTANT:** This project uses **Bun** exclusively for package management and script execution.

- Use `bun install` to install dependencies
- Use `bun add <package>` to add dependencies
- Use `bun run <script>` to run scripts
- **DO NOT** use pnpm, yarn, or npm

## Versioning: bumping `@kinotic-ai/core`

Other packages declare `@kinotic-ai/core` as a **peerDependency** (e.g. `os-api`, `persistence`) with a `>=<version>` floor, plus a `workspace:*` devDependency for local builds. The `workspace:*` devDep tracks the local build automatically, but the peer floor is what a published consumer resolves against — so it does not move on its own.

Whenever you bump core's version, in the same change:

1. Raise the `@kinotic-ai/core` peerDependency floor to `>=<new core version>` in **every** package that declares it (grep `@kinotic-ai/core` across `packages/*/package.json`).
2. Bump that dependent package's own `version` so the new floor actually ships — a published consumer using `persistence` directly then pulls a core that has the API `persistence` was built against.

This keeps `os-api`/`persistence` consumers from resolving an older core that lacks the symbols those packages now expect.

## Kinotic Service Registration

**IMPORTANT:** Any service class that needs to be called remotely via the Kinotic event bus **must** use the `@Publish` decorator from `@kinotic-ai/core`. Without `@Publish`, the service will not be registered with the `ServiceRegistry` and will not be accessible through service proxies.

```typescript
import { Publish } from '@kinotic-ai/core'

@Publish('org.kinotic.your.namespace')
export class YourService {
    // methods callable via IServiceProxy.invoke()
}
```

The `@Publish` decorator takes an optional `namespace` and optional `name` (defaults to the class name). Related decorators include `@Scope` on a getter or method (for routing to specific service instances), `@Version` (for semantic versioning), and `@Zones` (declares the zones a service is addressable in, appended to `Kinotic.zonePrefix`; without a declaration, `Kinotic.defaultZones` from the project package.json `kinotic.zones` field applies).

