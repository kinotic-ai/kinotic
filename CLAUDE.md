# kinotic

## Building in Claude Code Cloud

The cloud environment has JDK 21 installed but the project requires JDK 25. Download it first if not already present (Oracle CDN is in the egress allowlist):

```bash
curl -sL "https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz" -o /tmp/jdk25.tar.gz
cd /tmp && tar xzf jdk25.tar.gz
```

Then build with JDK 21 as the Gradle daemon (has the egress proxy CA certs) and JDK 25 for compilation:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :kinotic-core:compileJava \
  -Porg.gradle.java.installations.paths=/tmp/jdk-25.0.2
```

If the build fails resolving jreleaser or node-gradle plugins (403 from the Gradle plugin portal), add `CLAUDE_CLOUD_COMPILE=true`. This swaps to convention plugins that omit jreleaser/publishing and excludes kinotic-frontend:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
CLAUDE_CLOUD_COMPILE=true ./gradlew :kinotic-core:compileJava \
  -Porg.gradle.java.installations.paths=/tmp/jdk-25.0.2
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

Configuration follows the same split: `api/config/` contains `@ConfigurationProperties` classes and settings POJOs meant to be configured by users, while `internal/config/` contains Spring `@Configuration` classes that wire beans internally. This applies to all modules.

## Publishing Services

Java services that need to be called remotely must have `@Publish` on the interface:
This should only be done for services that are needed to be used remotely. i.e. from the frontend.

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

## Documentation

Public, user-facing documentation lives on the docsite at `website/content/` (Nuxt + Docus). Module READMEs and `docs/*.md` are for in-repo reference only — when documentation needs to be discoverable by platform operators, app developers, or org admins, it belongs on the docsite.

When changing code in any of these areas, also check the corresponding docsite page(s) for accuracy and update them in the same change:

| Code area | Docsite pages to check |
|---|---|
| Auth / IAM (`OidcLoginHandler`, `OidcSignupHandler`, `SignUpHandler`, `IamUser`, `OidcConfiguration`, `PendingRegistration`, `PlatformOidcBootstrap`, signup/login services) | `2.platform/4.organization-management.md`, `2.platform/5.system-security.md`, `1.apps/6.security/2.authentication.md` |
| Public REST endpoints under `/api/*` | `2.platform/4.organization-management.md` (endpoint reference table) |
| Configuration properties (`KinoticProperties` + subclasses, helm values, env vars) | `2.platform/3.configuration.md` |
| Deployment topology, ports, services, ingress | `2.platform/2.deployment-guide.md` |
| Migration grammar / SQL | `3.reference/2.migration-sql-grammar.md` and `1.apps/5.persistence/7.migrations.md` |
| Public API: services, decorators, CRI, SDK | `1.apps/4.services/*`, `3.reference/1.decorators.md`, `3.reference/4.cri-format.md`, `3.reference/5.sdk-packages.md` |

When dropping or renaming public concepts, grep the docsite for stale references before considering the change complete:

```bash
grep -rn "<old-name>" website/content/
```

In-repo docs (`docs/local-oidc-setup.md`, `docs/*.md`, module READMEs, javadoc) are operational/developer docs that complement the docsite, not replace it. They get the same scrutiny but the docsite is the canonical source for user-facing content.
