# kinotic-js/workspace

## Package Manager

**IMPORTANT:** This project uses **Bun** exclusively for package management and script execution.

- Use `bun install` to install dependencies
- Use `bun add <package>` to add dependencies
- Use `bun run <script>` to run scripts
- **DO NOT** use pnpm, yarn, or npm

## Kinotic Service Registration

**IMPORTANT:** Any service class that needs to be called remotely via the Kinotic event bus **must** use the `@Publish` decorator from `@kinotic-ai/core`. Without `@Publish`, the service will not be registered with the `ServiceRegistry` and will not be accessible through service proxies.

```typescript
import { Publish } from '@kinotic-ai/core'

@Publish('org.kinotic.your.namespace')
export class YourService {
    // methods callable via IServiceProxy.invoke()
}
```

The `@Publish` decorator takes a `namespace` (required) and optional `name` (defaults to the class name). Related decorators include `@Scope` (for routing to specific service instances) and `@Version` (for semantic versioning).

