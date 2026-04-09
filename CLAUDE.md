# kinotic

## Building in Claude Code Cloud

When running in the Claude Code cloud environment, always prefix Gradle commands with `CLAUDE_CLOUD_COMPILE=true`. This single flag:
- Skips jreleaser/publishing (unavailable in cloud due to Gradle plugin portal restrictions)
- Excludes kinotic-frontend (node-gradle plugin also unavailable)
- Falls back to Java 21 toolchain (cloud environment lacks JDK 25)

```bash
CLAUDE_CLOUD_COMPILE=true ./gradlew :kinotic-domain:compileJava
```

This flag has no effect on normal builds — omitting it uses the default Java 25 toolchain with full publishing and frontend support.

## Java Conventions

Always use Lombok where possible: `@Getter`, `@Setter`, `@Accessors(chain = true)`, `@NoArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j`, `@Data`, `@Builder`. Prefer `@RequiredArgsConstructor` over hand-written constructors for dependency injection. Use `@Slf4j` instead of manual `LoggerFactory.getLogger()` calls.

## Package Structure

Both Java and TypeScript modules follow the same layout convention. The rule is: if something will be used by another module/node, it belongs in `api/`. If not, it belongs in `internal/`. The `internal/` structure mirrors `api/` for implementations.

- `api/` — Public interfaces, types, and DTOs used by other modules or nodes (shared/exported)
- `internal/` — Everything private to this module (not shared/exported)
  - `internal/api/` — Implementations of public `api/` interfaces (`@Publish`, `@Component`, etc.)
  - `internal/services/` — Service proxies and services only used within this module
  - `internal/model/` — DTOs and value objects only used within this module

The `internal/api/` structure mirrors `api/` for implementations. Example: `api/services/ITodoService` -> `internal/api/services/DefaultTodoService`. Use `internal/services/` or `internal/model/` for things that don't correspond to a public interface and should stay private.

## Publishing Services

Java services that need to be called remotely must have `@Publish` on the interface:

```java
@Publish
public interface MyService extends IdentifiableCrudService<MyEntity, String> { }
```

TypeScript services use the `@Publish` decorator with a namespace and optionally `@Scope` for instance routing:

```typescript
@Publish('my-namespace')
export class MyService {
    @Scope
    public readonly nodeId: string
}
```

## Proxying Services

To call a remote service from Java, annotate an interface with `@Proxy`. Use `@Scope` on a method parameter to route to a specific service instance:

```java
@Proxy(namespace = "my-namespace", name = "MyService")
public interface MyServiceProxy {
    CompletableFuture<Result> doSomething(@Scope String nodeId, String arg);
}
```

The `@Scope` parameter is stripped before dispatch to the backend method. A `package-info.java` with `@Version` is required for the proxy package.

From TypeScript, use `Kinotic.serviceProxy('fully.qualified.ServiceName')` and call `.invoke('methodName', [args])`.

## Migrations

Elasticsearch index migrations live in `kinotic-migration/src/main/resources/migrations/`.

Files must follow `V<N>__<description>.sql` naming (e.g. `V2__add_widgets.sql`). Versions must be unique positive integers. Type mappings:

And use the ANTLR4 grammar in `kinotic-migration/src/main/antlr4/KinoticMigration.g4`.
