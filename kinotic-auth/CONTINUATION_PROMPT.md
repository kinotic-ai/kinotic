# Continuation Prompt for Claude Code CLI

Copy everything below the line and paste it as your first message in a new `claude` CLI session from the kinotic repo root on branch `claude/gateway-abac-auth-9NlqY`.

---

I'm building an ABAC (Attribute-Based Access Control) system for the kinotic platform. There's a new `kinotic-auth` module on branch `claude/gateway-abac-auth-9NlqY` that's partially built. Here's where things stand:

## What's Done

**`kinotic-auth` module** — a new Gradle module with:

- **ANTLR grammar** (`src/main/antlr/AbacPolicy.g4`) — defines a policy expression language supporting: `==`, `!=`, `<`, `>`, `<=`, `>=`, `in`, `contains`, `exists`, `like`, `and`, `or`, `not`, parentheses. Case-insensitive keywords. Paths are dotted identifiers like `participant.role`, `entity.department`, `order.amount`.

- **Sealed AST types** (`org.kinotic.auth.api.expressions`) — `PolicyExpression` (sealed) with `AndExpression`, `OrExpression`, `NotExpression`, `ComparisonExpression`. Operands are `AttributePath`, `LiteralValue`, `ArrayValue`.

- **ANTLR-based parser** (`org.kinotic.auth.parsers.PolicyExpressionParser`) — uses a visitor over the ANTLR parse tree to produce the AST. Single entry point: `PolicyExpressionParser.parse(String)`.

- **Cedar compiler** (`org.kinotic.auth.compilers.CedarCompiler`) — AST to Cedar condition text. Maps `participant.*` to Cedar's `principal.*`, `context.*` stays as-is, everything else becomes `resource.*`.

- **ES query compiler** (`org.kinotic.auth.compilers.EsQueryCompiler`) — AST to Elasticsearch `Query`. Resolves `participant.*` paths against a provided attributes map at compile time, producing `term`, `terms`, `range`, `exists`, `wildcard`, and `bool` queries. Uses the same ES client patterns as `kinotic-sql/QueryBuilder.java`.

- **`@AbacPolicy` annotation** (`org.kinotic.auth.api.annotations`) — repeatable annotation for `@Publish` service methods.

- **`AbacPolicyDecorator`** (`org.kinotic.auth.api.decorators`) — C3 decorator for entity definitions, extends `C3Decorator`.

- **Unit tests** for the parser and Cedar compiler.

- **CLAUDE.md** for the module.

## Key Design Decisions

1. **`participant` not `principal`** — The expression language uses `participant` to match the existing Kinotic `Participant` interface. The Cedar compiler maps this to Cedar's `principal` in output.

2. **Named parameters for service methods** — In `@AbacPolicy("participant.role contains 'finance' and order.amount < 50000")` on a method `placeOrder(Order order, Participant participant)`, `order` refers to the first method parameter. The compiler resolves parameter names to positional indices (`args[0]`) at publish/registration time using the method signature. `Participant` parameters are excluded from positional indexing since they're injected from the `SENDER_HEADER`.

3. **Two evaluation points**:
   - **Gateway** (`EndpointConnectionHandler.send()`) — for JS-to-JS proxy calls where no Java service invocation happens. Uses streaming JSON field extraction only when policies reference payload attributes.
   - **Invoker** (`ServiceInvocationSupervisor.processInvocationRequest()`) — for Java service calls, after `argumentResolver.resolveArguments()` where JSON is already parsed. Zero additional parsing cost.

4. **ES query compilation for reads** — Instead of fetching all records and filtering post-hoc, `@Match` trees compile directly to ES `bool` filters injected into read queries. No Cerbos needed — we bypass it entirely and compile our own AST to both Cedar (for allow/deny) and ES DSL (for query filtering).

5. **No Cedar or Cerbos dependency in kinotic-auth** — The module is engine-agnostic. Cedar SDK integration would go in the consuming module.

## What's Next

These are the remaining tasks, roughly in order:

1. **Integration with `ServiceInvocationSupervisor`** — Add an authorization check between `argumentResolver.resolveArguments()` and `handlerMethod.invoke()` in `processInvocationRequest()`. Build Cedar context from the resolved Java objects.

2. **Integration with `EndpointConnectionHandler.send()`** — For proxy calls, evaluate policies at the gateway. Pre-compute which CRI patterns need payload evaluation. Use Jackson streaming to extract only referenced fields when needed.

3. **Parameter name resolution** — At `@Publish` service registration time (`ServiceRegistrationBeanPostProcessor`), extract `@AbacPolicy` annotations, resolve parameter names to positional indices, and register compiled policies keyed by CRI + method path.

4. **ES query filter injection** — In the persistence layer's read path, compile entity definition `AbacPolicyDecorator` expressions to ES queries and merge them with user queries.

5. **Remove old persistence auth** — Replace `AuthorizationServiceFactory`, `PolicyAuthorizer`, `PolicyEvaluator`, `PreAuthorizationExecutor` and related classes with the new ABAC system.

Please read the `kinotic-auth/CLAUDE.md` and the key source files to familiarize yourself with the module before proceeding.
