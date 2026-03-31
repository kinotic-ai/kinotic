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
- The `CedarCompiler` maps `participant.*` to Cedar `principal.*` and all other roots (entity, method parameters) to `resource.*`.

## Package Structure

| Package | Responsibility |
|---|---|
| `org.kinotic.auth.api.annotations` | `@AbacPolicy` and `@AbacPolicies` annotations for published Java service methods |
| `org.kinotic.auth.api.decorators` | `AbacPolicyDecorator` for attaching policies to entity definitions via C3 IDL |
| `org.kinotic.auth.api.expressions` | Sealed AST types: `PolicyExpression`, `ComparisonExpression`, `AndExpression`, `OrExpression`, `NotExpression`, `AttributePath`, `LiteralValue`, `ArrayValue` |
| `org.kinotic.auth.parser` | **ANTLR-generated** lexer, parser, visitor, and listener — do not edit |
| `org.kinotic.auth.parsers` | Hand-written `PolicyExpressionParser` (ANTLR visitor that produces the AST) and `PolicyParseException` |
| `org.kinotic.auth.compilers` | `CedarCompiler` (AST → Cedar condition string) and `EsQueryCompiler` (AST → Elasticsearch `Query`) |

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
| `CedarCompiler` | `PolicyExpression` AST | Cedar condition string (body of a `when` clause) | Gateway-level allow/deny evaluation via Cedar in-process JNI |
| `EsQueryCompiler` | `PolicyExpression` AST + participant attributes map | Elasticsearch `Query` | Injected as filter into read queries so only authorized documents are returned |

For service method policies, the gateway transforms raw JSON argument arrays into named objects using registered parameter names. The CedarCompiler maps these parameter names to `resource.*` attributes, enabling Cedar expressions like `resource.order.amount < 50000` for a method parameter named `order`.

## Module Dependencies

| Module | Reason |
|---|---|
| `kinotic-idl` | `C3Decorator`, `DecoratorTarget` used by `AbacPolicyDecorator` |
| `co.elastic.clients:elasticsearch-java` | Elasticsearch `Query`, `BoolQuery`, `FieldValue` types used by `EsQueryCompiler` |
| `com.cedarpolicy:cedar-java` | Cedar authorization engine for in-process policy evaluation via JNI |
| `org.antlr:antlr4-runtime` | ANTLR runtime for the generated parser |
