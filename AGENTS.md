# kinotic

## Building in Codex Cloud

The cloud environment has JDK 21 installed, but the project requires JDK 25. Download it first if not already present (Oracle CDN is in the egress allowlist):

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

## Explain with code, not prose

The maintainers of this repo read code faster than English. When explaining anything that has a code representation — a design decision, a trade-off, a bug, an API, a proposed change — show the code itself and use prose only as connective tissue:

- Lead with the relevant snippet, quoted from the actual repo with `path:line` references — not a paragraph describing it.
- Present options and trade-offs as side-by-side code blocks the reader can compare directly, with a short comment marking the line where they differ. Let the code carry the comparison; one sentence per option for what the code can't show.
- Never describe code indirectly when you can show it. A sentence about what a change does to an API is opaque; the call site that now compiles (or no longer compiles), with a one-line comment, is immediately legible.
- Show failure modes as code that compiles-but-misbehaves (or the verbatim compiler/test error), not as an abstract description of the risk.
- Keep prose for what code cannot express: intent, constraints, and consequences — one or two sentences placed next to the snippet they explain.

This governs how you communicate *about* the code in conversation — chat replies, PR descriptions, review responses. It does not apply to the repo's own artifacts: documentation (this file, READMEs) and code comments.


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

Keep comments concise — usually a single line. Spend more words only where the logic is genuinely not straightforward and the extra explanation earns its place: a subtle invariant, a non-obvious interaction, a reason that isn't visible in the code. A comment's length should track how hard the code is to follow, not how important the code is.

Comment the code as it is now, not its history. Don't narrate what the code used to do or the edit you're making — no "before", "previously", "used to", "changed from", or "now does X instead" phrasing. The diff and git history record what changed; the comment describes the present state.

Use standard programming vocabulary in comments — the terms from GoF, Fowler's Refactoring, and the JDK/framework docs: delegate, factory, guard clause, invariant, idempotent, race condition, callback, dispatch. Never literary metaphors or coined phrases ("prologue", "dance", "journey", "saga"). Reference the actual method and class names involved rather than describing them indirectly. State cause and effect directly.

Never remove or alter an existing authorship comment — `Created by <name> on <date>`, `@author`, or similar attribution. Preserve it verbatim (name, accents, emoji, date, punctuation) when editing or refactoring a file, including when you rewrite the surrounding Javadoc/JSDoc, and carry it with the type if you move that type to another file.

## Properties
Properties should never be created for something that will not need to be configured differently in different environments. i.e. Kinotic Cloud dev vs Kinotic Cloud prod. In the case of a route or something that will be the same for multiple environments, create a constant.

Never gate a bean on a Spring profile — `@Profile` is for test contexts only; profile-gated beans are hard to audit. Profiles are property bundles: an `application-<name>.yml` selects property values for a deployment shape. Enabling or disabling behavior is done with explicit `kinotic.*` properties read by `@ConditionalOnProperty` (the `kinotic.disable*` module flags are the established idiom), so what a deployment runs can be read from its YAML alone.

## Dependency Versions

Never hardcode a dependency version in a module `build.gradle`. Every version lives as a `*Version` property in `gradle.properties` (kept alphabetical) and is pinned once in the `dependencyManagement` block of `buildSrc/src/main/groovy/org.kinotic.java-common-conventions.gradle`. The module declares the artifact with no version, so the managed version applies.

One version per artifact across every module, one place to bump it. A literal version repeated across modules is Shotgun Surgery; the same artifact pinned at two versions in two files is a latent bug. Verify a move with `dependencyInsight` on the module's `compileClasspath` — `selected by rule` confirms the managed version is in effect.

## Keep docs in sync with code

When a change alters something the docs describe — a wire contract, public API signature, REST route, auth mechanism, configuration option, or user-facing behavior — update the affected docs in the same change. `website/content/**` must always reflect the correct and current shape of the system; stale docs are a defect, not a follow-up. Before finishing, grep `website/content` for the symbols, routes, and field names you changed and reconcile every hit. If a change is genuinely too large to document in the same pass, say so explicitly rather than leaving the docs silently wrong.

## Avoid these code smells

These are the named smells from Martin Fowler and Kent Beck's catalog in *Refactoring: Improving the Design of Existing Code*, grouped by Mäntylä's taxonomy — decades of industry consensus on what makes code hard to change, not house style or one reviewer's taste. That is why they bind: each one is a pattern the field has repeatedly watched turn into maintenance cost. Check every diff against this list before presenting it. The bar for any new abstraction is YAGNI (Beck and Jeffries, Extreme Programming) and the Rule of Three (Don Roberts, in Refactoring): it must carry information or remove duplication **today** — not that it might someday.

**Dispensables — code that should not exist**

- **Speculative Generality.** No one-value enums, no parameters every call site passes the same constant to, no "seam for a future toggle," no interface with a single implementation created "just in case," no config nobody asked for. Build for the current requirement; introduce the discriminator or abstraction when the second concrete case exists to design against.
- **Dead Code.** No defensive branches every caller already makes impossible, no unused parameters or imports, no commented-out code, no "kept for later" methods without an owner's explicit say-so. A guard that is genuinely load-bearing against a corrupted state earns an inline comment saying so — otherwise delete it.
- **Lazy Element.** A class, method, or package that no longer pulls its weight after a refactor gets inlined or deleted, not left behind.
- **Duplicated Code.** Before writing new logic, find the existing seam and compose it (one logic path). Two near-identical blocks in sibling classes means the shared piece was never extracted — extract it to the nearest common layer, not to a new grab bag.
- **Comments as deodorant.** A comment explaining confusing code is a signal to fix the code. The Comments section above governs what comments are for.

**Bloaters — things that have grown past one responsibility**

- **Long Function.** A method that does several things at different levels of abstraction gets decomposed, each piece named for what it does.
- **Large Class / junk drawers.** The smell is a class that accumulates *unrelated* members and changes for many reasons; split it along the reasons it changes. Entities too: a class holding one kind of thing must not carry a name claiming generality it doesn't have. The name itself isn't the smell: a shared `Util`/`Constants`/`Helper` class is fine for cohesive, stateless, well-named static members — a single known home beats scattering them. Don't overcorrect either — a class or file created to hold a single static method is a Lazy Element; fold it into the nearest cohesive home instead of trading a grab bag for file sprawl.
- **Long Parameter List / flag arguments.** A boolean or mode parameter that forks a method's whole behavior is two methods. More than ~4 parameters is a sign some of them are a missing type.
- **Data Clumps.** Values that always travel together belong in one type, not loose parameter pairs repeated across signatures.
- **Primitive Obsession.** Covered by the enum rule in Java Conventions — applies equally to ids, keys, and wire codes used across boundaries.

**Couplers — classes that know too much about each other**

- **Middle Man / needless indirection.** No support class, wrapper, or dispatch layer with a single consumer — inline it until at least two real consumers exist; a test is never the second consumer. A flow's logic should be followable inside one class; if understanding it requires hopping between classes, the indirection is the smell. Same rule for constants: used in one class → declared in that class; shared catalogs only for genuine cross-class or cross-boundary contracts.
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

## Tests serve the code — they never shape it

The smells catalog binds with no testability exception: never extract, export, widen
visibility, or add a parameter/seam whose only consumer is a test. A test is never the
second consumer under the Rule of Three. Test through the interface a real caller uses
and assert on outputs the code already produces (the file written, the response returned,
the process killed); reach edge branches by controlling real inputs, not by opening
internals. Prefer one behavioral test with real infrastructure (processes, temp dirs,
containers) — gated to skip when the environment lacks it — over unit tests that each
cost a structural concession. Behavior unobservable through any public interface is a
production API gap: raise it, don't add a test-only door.

The same preference governs test level: an e2e/integration test against real
collaborators beats a unit test that must fake or mock significant functionality — the
faked collaborator is where the bugs live (wire dispatch, deserialization, persistence
hooks), and a slower test that exercises them is worth more than a fast one that assumes
them. The exception is logic with no external collaborators (parsing, guards, pure
transforms): there a unit test localizes failures earlier and more precisely than any
e2e test can, and needs no fakes to begin with.
