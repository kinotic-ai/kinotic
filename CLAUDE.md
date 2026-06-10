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

Use standard programming vocabulary in comments — the terms from GoF, Fowler's Refactoring, and the JDK/framework docs: delegate, factory, guard clause, invariant, idempotent, race condition, callback, dispatch. Never literary metaphors or coined phrases ("prologue", "dance", "journey", "saga"). Reference the actual method and class names involved rather than describing them indirectly. State cause and effect directly.

Never remove or alter an existing authorship comment — `Created by <name> on <date>`, `@author`, or similar attribution. Preserve it verbatim (name, accents, emoji, date, punctuation) when editing or refactoring a file, including when you rewrite the surrounding Javadoc/JSDoc, and carry it with the type if you move that type to another file.

## Properties
Properties should never be created for something that will not need to be configured differently in different environments. i.e. Kinotic Cloud dev vs Kinotic Cloud prod. In the case of a route or something that will be the same for multiple environments, create a constant.

## Avoid these code smells

Check every diff against this list before presenting it. These are the standard smells from Fowler's Refactoring (grouped by the usual taxonomy); the bar for a new abstraction is that it carries information or removes duplication **today** — not that it might someday.

**Dispensables — code that should not exist**

- **Speculative Generality.** No one-value enums, no parameters every call site passes the same constant to, no "seam for a future toggle," no interface with a single implementation created "just in case," no config nobody asked for. Build for the current requirement; introduce the discriminator or abstraction when the second concrete case exists to design against.
- **Dead Code.** No defensive branches every caller already makes impossible, no unused parameters or imports, no commented-out code, no "kept for later" methods without an owner's explicit say-so. A guard that is genuinely load-bearing against a corrupted state earns an inline comment saying so — otherwise delete it.
- **Lazy Element.** A class, method, or package that no longer pulls its weight after a refactor gets inlined or deleted, not left behind.
- **Duplicated Code.** Before writing new logic, find the existing seam and compose it (one logic path). Two near-identical blocks in sibling classes means the shared piece was never extracted — extract it to the nearest common layer, not to a new grab bag.
- **Comments as deodorant.** A comment explaining confusing code is a signal to fix the code. The Comments section above governs what comments are for.

**Bloaters — things that have grown past one responsibility**

- **Long Function.** A method that does several things at different levels of abstraction gets decomposed, each piece named for what it does.
- **Large Class / junk drawers.** No `Constants`/`Utils`/`Helper`/`Manager` grab bags accumulating unrelated members. Name a class for the one thing it holds; if you can't name it honestly, split it. Entities too: a class holding one kind of thing must not carry a name claiming generality it doesn't have.
- **Long Parameter List / flag arguments.** A boolean or mode parameter that forks a method's whole behavior is two methods. More than ~4 parameters is a sign some of them are a missing type.
- **Data Clumps.** Values that always travel together belong in one type, not loose parameter pairs repeated across signatures. Exception by convention: the `organizationId`/`applicationId` pair stays as two nullable parameters everywhere, because the null-shape of the pair is what encodes scope (SYSTEM/ORGANIZATION/APPLICATION) on entities, paths, and published signatures — do not "fix" it into a wrapper type.
- **Primitive Obsession.** Covered by the enum rule in Java Conventions — applies equally to ids, keys, and wire codes used across boundaries.

**Couplers — classes that know too much about each other**

- **Middle Man / needless indirection.** No support class, wrapper, or dispatch layer with a single consumer — inline it until at least two real consumers exist. A flow's logic should be followable inside one class; if understanding it requires hopping between classes, the indirection is the smell. Same rule for constants: used in one class → declared in that class; shared catalogs only for genuine cross-class or cross-boundary contracts.
- **Feature Envy.** A method that mostly reads and combines another class's data belongs on that class. If a handler keeps reaching into an entity to make a decision the entity could make, move the decision.
- **Inappropriate Intimacy.** Don't reach through another class's internals (its repository, its private collaborators) — go through its interface. Crossing the `api`/`internal` boundary from another module is this smell by definition.
- **Message Chains.** `a.getB().getC().getD()` couples the caller to the whole path. Ask the nearest object for what you actually need.

**Change preventers — structure that makes edits expensive**

- **Shotgun Surgery.** When one logical change forces edits in many files (a wire code, a route, a field name), the knowledge is scattered — give it a single home first, then change it.
- **Divergent Change.** When one class keeps changing for unrelated reasons, it has multiple responsibilities — split along the reasons it changes.
- **Repeated Switches.** The same `switch`/`if-else` chain over the same discriminator in more than one place means polymorphism or a map is missing. One occurrence is fine; the second copy is the smell.

**Object-orientation abusers**

- **Alternative Classes with Different Interfaces.** Two classes doing the same job must share a shape: same method names, same parameter order.
- **Refused Bequest.** Don't extend a base class to use one method while ignoring or overriding-to-nothing the rest — compose instead.
- **Temporary Field.** A field only meaningful during one operation is a missing parameter or a missing small object, not state.
- **Data Class with leaked logic.** Entities/DTOs stay dumb (this codebase's convention), but the logic operating on them must then live in ONE service — not spread across every caller.
