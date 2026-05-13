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

## Don't guess from names

Names suggest meaning but don't define it. Before using an annotation, framework hook, base class, or library helper you haven't used in this codebase before, read its source or docs and confirm what it actually does. Don't infer behaviour from a plausible-sounding name and ship it. If you can't verify the behaviour, ask — don't write a comment justifying the guess.

## Single CRUD layer for system objects

Every persisted entity has exactly one service that owns its reads and writes — typically the `*Service` in `api/services/` whose default implementation extends `AbstractCrudService`. All callers go through that service. Don't reach around it: don't inject `CrudServiceTemplate` or `ElasticsearchAsyncClient` into a webhook handler, a config-loader, a worker, or any other ad-hoc class to run queries directly. If a method you need doesn't exist on the service, add it to the service.

This keeps every entity's read/write surface in one place, makes org-scope enforcement uniform, and means schema and persistence changes only have to be reflected in one class.

Cross-org access (webhook receivers, system bootstrap, cache loaders) goes through the same service, called inside `securityContext.withElevatedAccess(...)` to skip the per-call org filter.

## Java Conventions

Always use Lombok where possible: `@Getter`, `@Setter`, `@Accessors(chain = true)`, `@NoArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j`, `@Data`, `@Builder`. Prefer `@RequiredArgsConstructor` over hand-written constructors for dependency injection. Use `@Slf4j` instead of manual `LoggerFactory.getLogger()` calls.

Use `enum` for any field whose value is constrained to a known set — never `String` with magic-string constants. Spring and Vert.x both auto-coerce JSON strings to enum values when deserializing into typed POJOs (Jackson's `@JsonCreator` / case-insensitive matching is built-in), so the wire contract stays string-friendly while the in-process type catches typos at compile time. Examples: `AuthScopeType`, `AuthType`, `OidcProviderKind`. If a field is `String authScopeType` accepting `"ORGANIZATION"`/`"APPLICATION"`/`"SYSTEM"`, that's a special case — not a pattern to repeat.

## Package Structure

Both Java and TypeScript modules follow the same layout convention. The rule is: if something will be used by another module/node, it belongs in `api/`. If not, it belongs in `internal/`. The `internal/` structure mirrors `api/` for implementations.

- `api/` — Public interfaces, types, and DTOs used by other modules or nodes (shared/exported)
- `internal/` — Everything private to this module (not shared/exported)
  - `internal/api/` — Implementations of public `api/` interfaces (`@Publish`, `@Component`, etc.)
  - `internal/model/` — DTOs and value objects only used within this module

The `internal/api/` structure mirrors `api/` for implementations. Example: `api/services/ITodoService` -> `internal/api/services/DefaultTodoService`. 

When create code to store data call it a Service not a Store. If the Service needs to do other work before storing the data the persistence layer should be called a Repsoitory and the service should delegate to it.

Configuration follows the same split: `api/config/` contains `@ConfigurationProperties` classes and settings POJOs meant to be configured by users, while `internal/config/` contains Spring `@Configuration` classes that wire beans internally. This applies to all modules.

Don't create a new package or folder to hold a single file. Single-file folders just spread related code across the tree without aiding discoverability. Place the file in the nearest existing package that fits. A new subpackage is justified once there are at least two or three related files that genuinely belong together.

## Comments

Javadoc — block comments on classes, methods, fields, anything else — describes the contract from the caller's perspective: what something is for, what guarantees it makes, what the inputs and outputs mean. It should not document implementation details — how the class persists, which helper it delegates to, what bypass mechanism it uses internally — that's noise for someone using the API and rots when the implementation changes.

Inline comments inside method bodies are different: they're for implementation details that aren't obvious from reading the code, and only when they aren't. A subtle invariant, the reason for an unusual ordering, a workaround for a specific bug, a non-obvious choice between two valid approaches — those earn an inline comment. Self-evident code does not. If you find yourself writing a comment that restates what the next line does, delete it.

## Properties
Properties should never be created for something that will not need to be configured differently in different environments. i.e. Kinotic Cloud dev vs Kinotic Cloud prod. In the case of a route or something that will be the same for multiple environments, create a constant. 