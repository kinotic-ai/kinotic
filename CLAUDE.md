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


## Terminology and prose

Use precise technical, design, and programming terminology in all prose — chat replies, explanations, design discussion, code review, commit messages, and PR descriptions. State the mechanism and behaviour literally and name the actual construct.

Do not use analogies, metaphors, idioms, figures of speech, or colloquialisms. Replace them with the precise term:

- "belt and suspenders" → redundant check / precondition assertion
- "can't happen" / "shouldn't happen" → unreachable state / invariant violation / illegal state
- "shipped" / "land it" → committed / merged / released / published
- "reach for" → use
- "the whole point of" → the purpose of
- "bridge" / "glue" → name the construct (type guard, adapter, facade, …)
- "under the hood" → internally / in the implementation
- "fail fast" → validate at the boundary and throw on an invalid precondition

When a precise term exists, use it rather than paraphrasing it into prose: "user-defined type guard with a type predicate", "control-flow narrowing", "structural subtype", "discriminated union", "precondition asserted at the authentication boundary", "compile error (TS2339)", "semantic versioning floor". This applies to spoken-style explanation as much as to written artifacts.


## Java Conventions

Always use Lombok where possible: `@Getter`, `@Setter`, `@Accessors(chain = true)`, `@NoArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j`, `@Data`, `@Builder`. Prefer `@RequiredArgsConstructor` over hand-written constructors for dependency injection. Use `@Slf4j` instead of manual `LoggerFactory.getLogger()` calls.

Use `enum` for any field whose value is constrained to a known set — never `String` with magic-string constants. Spring and Vert.x both auto-coerce JSON strings to enum values when deserializing into typed POJOs (Jackson's `@JsonCreator` / case-insensitive matching is built-in), so the wire contract stays string-friendly while the in-process type catches typos at compile time. Examples: `AuthScopeType`, `AuthType`, `OidcProviderKind`. If a field is `String authScopeType` accepting `"ORGANIZATION"`/`"APPLICATION"`/`"SYSTEM"`, that's a special case — not a pattern to repeat.

## Package Structure (Crucial!!)

Both Java and TypeScript modules follow the same layout convention. The rule is: if something will be used by another module/node, it belongs in `api/`. If not, it belongs in `internal/`. The `internal/` structure mirrors `api/` for implementations.

- `api/` — Public interfaces, types, and DTOs used by other modules or nodes (shared/exported)
- `internal/` — Everything private to this module (not shared/exported)
  - `internal/api/` — Implementations of public `api/` interfaces (`@Publish`, `@Component`, etc.)
  - `internal/model/` — DTOs and value objects only used within this module

The `internal/api/` structure mirrors `api/` for implementations. Example: `api/services/ITodoService` -> `internal/api/services/DefaultTodoService`. 

When creating code to store data, call it a Repository, not a Store. If there is advanced logic needed outside of CRUD, create a Service that delegates to the Repository.

Configuration follows the same split: `api/config/` contains `@ConfigurationProperties` classes and settings POJOs meant to be configured by users, while `internal/config/` contains Spring `@Configuration` classes that wire beans internally. This applies to all modules.

Don't create a new package or folder to hold a single file. Single-file folders just spread related code across the tree without aiding discoverability. Place the file in the nearest existing package that fits. A new subpackage is justified once there are at least two or three related files that genuinely belong together.

***Give every top-level type its own file. Don't nest a class, enum, or record inside an interface — DTOs, result types, and enums that appear in an interface's method signatures belong in their own files in the same package, not inlined in the interface body. Nesting buries types, makes them awkward to import, and bloats the interface. The same applies to types nested inside a class purely for convenience.***

## Comments

Javadoc — block comments on classes, methods, fields, anything else — describes the contract from the caller's perspective: what something is for, what guarantees it makes, what the inputs and outputs mean. It should not document implementation details — how the class persists, which helper it delegates to, what bypass mechanism it uses internally — that's noise for someone using the API and rots when the implementation changes. Also they should not document what something does not do. Only what it does do. (Unless it is a security concern, Does not validate user) 

Inline comments inside method bodies are different: they're for implementation details that aren't obvious from reading the code, and only when they aren't. A subtle invariant, the reason for an unusual ordering, a workaround for a specific bug, a non-obvious choice between two valid approaches — those earn an inline comment. Self-evident code does not. If you find yourself writing a comment that restates what the next line does, delete it.

The split is about audience, not formatting. Javadoc is for **consumers** of the API; inline is for **maintainers** of the body. Before writing a comment, ask which one needs it. The rationale for a defensive check, a workaround, or a tricky ordering belongs inline next to the code that does it — never in the Javadoc, even if it explains why the method behaves the way it does. The caller doesn't care that an org-mismatch returns null because of an ES shard-hashing edge case; they care that it returns null when there's no doc for that org. The "because" stays in the body.

Never remove or alter an existing authorship comment — `Created by <name> on <date>`, `@author`, or similar attribution. Preserve it verbatim (name, accents, emoji, date, punctuation) when editing or refactoring a file, including when you rewrite the surrounding Javadoc/JSDoc, and carry it with the type if you move that type to another file.

## Properties
Properties should never be created for something that will not need to be configured differently in different environments. i.e. Kinotic Cloud dev vs Kinotic Cloud prod. In the case of a route or something that will be the same for multiple environments, create a constant.

## Tenant vs Organization

These are distinct concepts — don't conflate them.

- **Organization** owns **Applications**. An Application belongs to exactly one Org.
- **Tenant** is an isolation scope for the **data that end users of an Application save** — i.e. instances of `Entity` types defined by an `EntityDefinition`. It exists so that Application developers can build multi-tenant applications where each end-user dataset is partitioned by `tenantId`.

So the hierarchy is: **Org → Application → (end-user data, partitioned by tenant)**. Tenants live underneath an Application; they are not a layer above Organizations.

Participant scope is encoded by subtype, not a flag: `SystemParticipant`, `OrganizationParticipant`, and `ApplicationParticipant` (which carries `organizationId`, `applicationId`, and an optional `tenantId`). For an entity operation, source the organization id from the bound participant — `context.getParticipant().getOrganizationId()` on the `EntityContext`'s `ApplicationParticipant` — never from `requireParticipant(OrganizationParticipant.class)` and never from a prefix baked into an entity id.
