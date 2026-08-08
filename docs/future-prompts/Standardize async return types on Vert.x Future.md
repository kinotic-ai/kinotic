# Standardize the platform's async return type on `io.vertx.core.Future`

Current-state claims below were measured against `develop` at `bf6270e` ("Raise first-party
floors after publish", 2026-08). Re-grep before acting on any of them — counts drift.

**STOP AT EVERY PHASE BOUNDARY.** When a phase is complete (implemented, tested, committed,
pushed), report what was done and wait for Navid's explicit approval before starting the next.
No preparatory work belonging to a later phase while waiting.

## Objective

Replace `CompletableFuture` with `io.vertx.core.Future` in the platform's service and
repository contracts, so one async type flows from the Elasticsearch boundary up through
repositories, services, and REST handlers. Conversion stays only where an external library
forces it.

## Why

Two independent reasons, both measured rather than assumed.

**1. The seams are numerous and almost all one removable shape.** 70 `fromCompletionStage`
/ `toCompletionStage` sites across the tree:

```
kinotic-domain 41   kinotic-github 11   kinotic-core 8
kinotic-persistence 7   kinotic-os-api 1   kinotic-telemetry 1   kinotic-test 1
```

In `kinotic-domain`, roughly 40 of the 41 are the same pattern — a Vert.x-native caller
(a `RoutingContext` handler, or an interface declared in core that already returns `Future`)
wrapping a service or repository that returns `CompletableFuture`. `InviteHandler`,
`OAuthServerHandler`, `OrganizationLoginHandler`, `OrganizationSignupHandler`,
`ApplicationLoginHandler`, `AuthEndpointSupport`, and `OidcFlowOrchestrator` account for ~28;
`ElasticServiceDirectoryStrategy` and `KinoticSecurityService` for ~11. All of them disappear
when the callee returns `Future`.

**2. The codebase is already hand-compensating for the context loss.** 17 of the 70 seams
pass an explicit context so the continuation lands back on the right Vert.x context:

```java
// KinoticSecurityService.java:192
return Future.fromCompletionStage(identityService.findById(sub), vertx.getOrCreateContext())
```

This matters because `SecurityContext` stores the authenticated participant in a Vert.x
`ContextLocal` (`SecurityContext.java:22-34`), so a continuation that resumes off-context
cannot read it. `Future` dispatches continuations on the context that created the promise and
gives that for free; `CompletableFuture` runs dependents on whichever thread completed the
stage and makes no such guarantee.

Be honest about the size of this second benefit: a sweep of all 49 participant-read call
sites found **no direct violation** in production code — the only deeply-nested reads are in
`kinotic-server`'s `DefaultTestService`, which exercises the invariant on purpose. The
discipline is currently held by convention plus those 17 manual context passes. So this is
preventive, not a bug fix. The one latent instance found is indirect and ungreppable:
`DefaultGitHubProjectRepoService.resolve` calls `installationService.findForCurrentOrg()`
(which reads the participant internally) from inside a `thenCompose` callback that runs after
an Elasticsearch round-trip. It is unpublished today (`// @Publish TODO`), so it has never
fired. Indirect reads of that shape are what a structural guarantee prevents and a convention
does not.

**3. The wire contract does not change.** Both gates already accept `Future` from a
`@Publish`ed method:

```java
// DefaultKinotic.java:80 — registered reactive type, so schema generation emits AsyncC3Type<T>
reactiveAdapterRegistry.registerReactiveType(
        ReactiveTypeDescriptor.singleOptionalValue(Future.class, …), …)
```
```
kinotic-core/…/api/service/rpc/types/VertxFutureRpcReturnValueHandler.java   // runtime dispatch
```

`ReactiveTypeConverter` resolves any registered adapter to `AsyncC3Type`/`StreamC3Type`, so a
method returning `Future<T>` produces the same C3 schema — and therefore the same generated
TypeScript — as `CompletableFuture<T>`. No SDK republish is forced by the type change itself.

**4. It is free right now and expensive later.** `kinoticVersion` is a `-SNAPSHOT` with
nothing released, so per the root `CLAUDE.md` these contracts can be reshaped without
deprecation shims. That stops being true at the first release.

## What stays `CompletableFuture`

Do not chase these to zero — a conversion at an external boundary is correct, it is
conversion scattered through business logic that is not.

- The Elasticsearch Java client's async API (`co.elastic.clients`).
- Caffeine's `AsyncLoadingCache` (e.g. `DefaultGitHubApiClient`'s token cache).
- Any Spring API that returns `CompletableFuture`.

`kinotic-persistence` is a judgment call worth making explicitly before Phase 2: it holds 504
`CompletableFuture` references but only 7 seams, meaning it is internally consistent and is
mostly *not* paying the conversion tax. Decide whether it migrates or stays the adapter layer
that converts once at the ES boundary. Migrating it is the largest single chunk of work in the
whole effort and buys the fewest seams.

## The hazard that will not fail to compile

Failure semantics differ, silently:

- `CompletableFuture` wraps a failure propagated through a chain in `CompletionException`
  (a direct `completeExceptionally` is not wrapped).
- Vert.x `Future` always hands the raw cause to `recover` / `otherwise` / `onFailure`.

This has already leaked into handler code, complete with an explanatory comment:

```java
// McpJsonRpcHandler.java:201
.otherwise(throwable -> {
    // a repository failure crosses the strategy's fromCompletionStage wrapped in CompletionException
    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
```

Every site that inspects, `instanceof`-checks, unwraps, or formats a `Throwable` can change
behavior while still compiling. Defensive unwraps like the one above become dead code and
should be deleted, not left in place. Phase 4 exists for this and must not be folded into an
earlier phase.

Also note the method-name mapping is not purely mechanical: `thenApply`→`map`,
`thenCompose`→`compose`, `thenAccept`→`onSuccess`, `exceptionally`→`otherwise`/`recover`,
`CompletableFuture.allOf`→`Future.all`, `completedFuture`→`succeededFuture`,
`failedFuture` keeps its name. `Future.all` returns `CompositeFuture`, whose `list()` yields
the results — it is not a drop-in for `allOf`'s `Void`.

## Phases

**Phase 0 — prove the contract is unchanged.** Flip exactly one published service end to end
(`GitHubAppInstallationService` is a good candidate: four methods, one consumer, already
audited). Generate the C3 schema and the TypeScript before and after and diff them. If they
differ, stop and re-plan — every later phase assumes they do not.

**Phase 1 — the bottom boundary.** Convert once, in one place: `CrudServiceTemplate` and the
repository base classes (`AbstractRepository`, `AbstractOrganizationScopedRepository`) expose
`Future` and absorb the Elasticsearch client's `CompletableFuture` internally. Nothing above
changes yet; the seams move down rather than disappearing, and the tree still builds.

**Phase 2 — the service contracts.** `CrudService` / `IdentifiableCrudService` in
`kinotic-core`, then `AbstractOrganizationScopedService` and the concrete services in domain,
os-api, github, orchestrator, and sql. This is the fulcrum: it flips five modules at once and
is the phase most likely to need splitting. Take the persistence decision above before
starting.

**Phase 3 — delete the seams.** The REST handlers and strategies now wrap `Future` in
`Future` — remove the `fromCompletionStage` calls and the 17 manual `getOrCreateContext()`
passes. This is where the payoff lands; expect the seam count to drop from 70 to whatever the
external boundaries genuinely require, and record that number.

**Phase 4 — error-semantics sweep.** Audit every `otherwise` / `recover` / `onFailure` /
`whenComplete` that touches a `Throwable`, delete now-unnecessary `CompletionException`
unwrapping, and check log/response messages that formatted `err.getMessage()` — those strings
change. Add a test anywhere a specific exception type drives a response code.

## Verifying

`kinotic-github` is a useful canary throughout: it is small (11 seams, 57 references), has
real tests that exercise a genuine async chain (`GitHubProjectRepoProvisionerTest`), and sits
between domain contracts above and a Vert.x-native client below, so it exercises both
directions of the change.
