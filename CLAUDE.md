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

Use `enum` for any field whose value is constrained to a known set — never `String` with magic-string constants. Spring and Vert.x both auto-coerce JSON strings to enum values when deserializing into typed POJOs (Jackson's `@JsonCreator` / case-insensitive matching is built-in), so the wire contract stays string-friendly while the in-process type catches typos at compile time. Examples: `AuthScopeType`, `AuthType`, `OidcProviderKind`. If a field is `String authScopeType` accepting `"ORGANIZATION"`/`"APPLICATION"`/`"SYSTEM"`, that's a refactor to do — not a pattern to repeat.

## Package Structure

Both Java and TypeScript modules follow the same layout convention. The rule is: if something will be used by another module/node, it belongs in `api/`. If not, it belongs in `internal/`. The `internal/` structure mirrors `api/` for implementations.

- `api/` — Public interfaces, types, and DTOs used by other modules or nodes (shared/exported)
- `internal/` — Everything private to this module (not shared/exported)
  - `internal/api/` — Implementations of public `api/` interfaces (`@Publish`, `@Component`, etc.)
  - `internal/services/` — Service proxies and services only used within this module
  - `internal/model/` — DTOs and value objects only used within this module

The `internal/api/` structure mirrors `api/` for implementations. Example: `api/services/ITodoService` -> `internal/api/services/DefaultTodoService`. Use `internal/services/` or `internal/model/` for things that don't correspond to a public interface and should stay private.

When create code to store data call it a Service not a Store. If the Service needs to do other work before storing the data the persistence layer should be called a Repsoitory and the service should delegate to it.

Configuration follows the same split: `api/config/` contains `@ConfigurationProperties` classes and settings POJOs meant to be configured by users, while `internal/config/` contains Spring `@Configuration` classes that wire beans internally. This applies to all modules.

Don't create a new package or folder to hold a single file. Single-file folders just spread related code across the tree without aiding discoverability. Place the file in the nearest existing package that fits. A new subpackage is justified once there are at least two or three related files that genuinely belong together.

## Properties
Properties should never be created for something that will not need to be configured differently in different environments. i.e. Kinotic Cloud dev vs Kinotic Cloud prod. In the case of a route or something that will be the same for multiple environments, create a constant. 