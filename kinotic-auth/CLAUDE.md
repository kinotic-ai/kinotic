# kinotic-auth — AI Reference

> ABAC (Attribute-Based Access Control) policy expression language, parser, and compilers.

## Build & Test Commands

```bash
./gradlew :kinotic-auth:build      # compile
./gradlew :kinotic-auth:test       # run unit tests
./gradlew :kinotic-auth:check      # build + test + lint
./gradlew :kinotic-auth:generateGrammarSource  # regenerate ANTLR parser from grammar
```

## Hard Rules

- Never edit generated parser files in `org.kinotic.auth.parser` directly — all changes go through `src/main/antlr/AbacPolicy.g4` and regenerate via `./gradlew :kinotic-auth:generateGrammarSource`.
- Hand-written visitor and parsing glue code belongs in `org.kinotic.auth.parsers` (plural) — the singular `org.kinotic.auth.parser` package is reserved for ANTLR-generated files.
- Always keep the `@AbacPolicy` annotation and `AbacPolicyDecorator` in `api` packages — they are part of the public surface consumed by `kinotic-core`, `kinotic-persistence`, and `kinotic-rpc-gateway`.
- The `EsQueryCompiler` must only produce document field references from resource/entity paths — participant and context paths must always be resolved to concrete values at compile time via the `participantAttributes` map.
- The `SpelCompiler` maps `participant.*` to `sub.*` and every other root (entity, method parameters) to `obj.<root>.*` — nested under the named-argument map by parameter name, matching the request the gateway builds.
- `SpelCompiler` may emit only map navigation, indexing, operators, literals and the registered `#contains`/`#like` functions — never method invocation, `T(...)`, `new` or `@bean` — because `SpelAuthorizationService` evaluates on a `SimpleEvaluationContext` that forbids all of those. Every path operand must stay null-guarded: SpEL orders null below every value, so an unguarded comparison against a missing attribute allows instead of denying.

## Package Structure

| Package | Responsibility |
|---|---|
| `org.kinotic.auth.api.annotations` | `@AbacPolicy` annotation for published Java service methods |
| `org.kinotic.auth.api.decorators` | `AbacPolicyDecorator` for attaching policies to entity definitions via C3 IDL |
| `org.kinotic.auth.api.expressions` | Sealed AST types: `PolicyExpression`, `ComparisonExpression`, `AndExpression`, `OrExpression`, `NotExpression`, `AttributePath`, `LiteralValue`, `ArrayValue` |
| `org.kinotic.auth.api.engine` | `AuthorizationEngine` SPI and `AuthorizationRequest` — the engine-neutral contract `SpelAuthorizationService` implements |
| `org.kinotic.auth.parser` | **ANTLR-generated** lexer, parser, visitor, and listener — do not edit |
| `org.kinotic.auth.parsers` | Hand-written `PolicyExpressionParser` (ANTLR visitor that produces the AST) and `PolicyParseException` |
| `org.kinotic.auth.compilers` | `SpelCompiler` (AST → SpEL expression) and `EsQueryCompiler` (AST → Elasticsearch `Query`) |
| `org.kinotic.auth.spel` | `SpelAuthorizationService` — the allow/deny engine: sandboxed SpEL (no methods, type references, constructors, bean references or assignment) with `SpelPolicyFunctions` providing `#contains` and `#like` |

## Expression Language

The grammar is defined in `src/main/antlr/AbacPolicy.g4`. Expressions follow this structure:

```
participant.role contains 'finance' and order.amount < 50000
entity.status in ['active', 'pending'] or participant.department == entity.department
not entity.deleted == true
entity.email like '*@kinotic.ai'
entity.approvedBy exists
```

**Operators** (precedence highest to lowest): comparisons (`==`, `!=`, `<`, `>`, `<=`, `>=`, `in`, `contains`, `exists`, `like`), `not`, `and`, `or`. Parentheses override precedence.

**Paths** use dotted notation: `participant.department`, `entity.address.city`, `context.time`. The root identifier is resolved contextually — `participant` and `context` are well-known; all others map to method parameter names (published services) or `entity` (entity definitions).

**Literals**: strings (`'value'`), integers (`42`), decimals (`3.14`), booleans (`true`/`false`). Keywords are case-insensitive.

## Compilation Targets

| Compiler | Input | Output | Use Case |
|---|---|---|---|
| `SpelCompiler` | `PolicyExpression` AST | SpEL boolean expression | Gateway-level allow/deny evaluation in-process by `SpelAuthorizationService` (the production engine) |
| `EsQueryCompiler` | `PolicyExpression` AST + participant attributes map | Elasticsearch `Query` | Injected as filter into read queries so only authorized documents are returned |

For service method policies, the gateway transforms raw JSON argument arrays into named objects using registered parameter names. `SpelCompiler` nests those names under `obj`, so a policy path `order.amount` evaluates as `obj.order.amount` for a method parameter named `order`.

## Module Dependencies

| Module | Reason |
|---|---|
| `kinotic-idl` | `C3Decorator`, `DecoratorTarget` used by `AbacPolicyDecorator` |
| `co.elastic.clients:elasticsearch-java` | Elasticsearch `Query`, `BoolQuery`, `FieldValue` types used by `EsQueryCompiler` |
| `org.springframework:spring-expression` | SpEL parser, compiler and `SimpleEvaluationContext` used by `SpelAuthorizationService` |
| `org.springframework:spring-context` | `MapAccessor`, the only property accessor on the sandboxed SpEL context |
| `org.antlr:antlr4-runtime` | ANTLR runtime for the generated parser |
