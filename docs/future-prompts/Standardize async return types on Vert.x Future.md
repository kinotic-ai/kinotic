# Standardize async return types on `io.vertx.core.Future`

Every number below was measured against `develop` at `c6eb14d` ("Console: mint login art marks
the system login", 2026-08). Re-grep before acting — they drift.

**STOP AT EVERY UNIT BOUNDARY.** When a unit is complete (implemented, tested, committed,
pushed), report and wait for Navid's explicit approval before starting the next.

## Goal

`CrudService` / `IdentifiableCrudService` join the `Future` side of kinotic-core's SPIs, and
the `CompletableFuture` → `Future` conversion plus its Vert.x context binding collapse into a
single function at the Elasticsearch client.

The goal is **core internal consistency**, not "fewer conversions". Fewer conversions is a
consequence.

## Why this and not something else

kinotic-core already declares its SPIs in two async types, split by consumer:

```
api/security/SecurityService.java            Future 1   CompletableFuture 0
api/directory/ServiceDirectoryStrategy.java  Future 7   CompletableFuture 0
api/crud/CrudService.java                    Future 0   CompletableFuture 9
api/crud/IdentifiableCrudService.java        Future 0   CompletableFuture 2
```

All 66 conversion seams sit on that fault line:

```
kinotic-domain 41   kinotic-github 12   kinotic-core 8
kinotic-persistence 2   kinotic-os-api 1   kinotic-telemetry 1   kinotic-test 1
of which 18 pass an explicit Vert.x context to compensate for context loss
```

The split is historical: the data stack was built when Structures was meant to stay Vert.x
agnostic. That goal was abandoned when the project moved to Kinotic's direction, so nothing
defends the `CompletableFuture` zone any more — it is residue, not a boundary.

**Do not justify this as a correctness fix.** `CrudServiceTemplate.bindToContext` already
re-binds ES continuations to the caller's Vert.x context specifically so
`SecurityContext.currentParticipant()` survives, and coverage is effectively complete
(`saveSync`→`save`, `createSync`→`create`, `deleteByIdSync`→`deleteById`, `findFirst`→`search`
all inherit it). `syncIndex` is the only public method that returns unbound, and nothing reads
a participant after an index refresh. The safety argument is about making the invariant
structural rather than remembered — not about fixing live bugs.

## The single conversion point

This is what "closer to the ES surface" means concretely:

```java
// now — a helper that must be remembered, 20 call sites across 16 public methods,
// and syncIndex forgot it
private <T> CompletableFuture<T> bindToContext(CompletableFuture<T> original) {
    Context ctx = Vertx.currentContext();
    if (ctx == null) return original;
    CompletableFuture<T> bound = new CompletableFuture<>();
    original.whenComplete((result, err) -> ctx.runOnContext(v -> { … }));
    return bound;
}

// after — the conversion IS the return type, so omitting it does not compile
private <T> Future<T> toFuture(CompletableFuture<T> es) {
    Promise<T> promise = Promise.promise();      // captures the calling Vert.x context
    es.whenComplete((r, err) -> { if (err != null) promise.fail(err); else promise.complete(r); });
    return promise.future();                     // continuations emit on that context
}
```

**Verify this before anything else:** that `Promise.promise()` created on a Vert.x context
dispatches `onComplete` handlers back onto it when completed from a foreign thread, and that
off-context it degrades the way `bindToContext`'s `ctx == null` branch does. The whole design
rests on it. Write a throwaway test with a `CompletableFuture` completed from a plain
`Thread`, assert `Vertx.currentContext()` inside the continuation.

## Unit 1 — the data stack (one commit)

```
CrudServiceTemplate                 16 public methods, 20 bindToContext sites → toFuture
repository subclasses               20 classes
CrudService / IdentifiableCrudService  (kinotic-core)
service subclasses                  16 classes
persistence entity API              JsonEntitiesRepository 15, AdminJsonEntitiesRepository 10
```

`kinotic-persistence` is **in** this unit, not a later decision. It depends on
`:kinotic-core` and `:kinotic-domain`, and `DefaultEntityService`, `DefaultEntityDefinitionService`,
`EntityServiceCache`, `EntityDefinitionRepository`, and `NamedQueriesDefinitionRepository` all
ride `CrudServiceTemplate`. When the template returns `Future`, the entity API either flips or
grows conversions — there is no third option, so flip it.

**Do this as one commit, not staged by layer.** Splitting repositories from services forces a
temporary `.toCompletionStage().toCompletableFuture()` into every delegating method of
`AbstractOrganizationScopedService`, which silently re-introduces `CompletionException`
wrapping across all 16 service classes, then removes it again. That is two invisible behavior
changes in opposite directions, and it puts a tree on `develop` that compiles, passes most
tests, and propagates errors like neither the before nor the after state. The rename mapping
is type-directed, so the compiler enumerates every site; the size is effort, not risk.

## Unit 2 — the auth satellites

Not CrudService-derived, so Unit 1 does not reach them, and they are where the rest of the
handler seams live (`InviteHandler` 7, `OAuthServerHandler` 7):

```
domain/api/services/security/InviteService              6
domain/api/services/SecretStorageService                6
domain/api/services/security/RefreshTokenService        5
domain/api/services/security/OAuthAuthorizationService  5
domain/api/services/security/SignUpService              4
domain/api/services/security/DeviceCodeGrantService     3
domain/api/services/security/LocalAuthenticationService 2
os/api/services/SystemOrganizationService               9
os/api/services/security/MemberService                  7
os/api/services/security/MachineService                 5
os/api/services/security/OAuthApprovalService           4
os/api/services/security/DelegateService                4
```

`SecurityService` itself needs nothing — it is already `Future`.

`kinotic-orchestrator`'s `VmManagerProxy` (6), `VmNodeOrchestrationService` (5), and
`WorkloadOrchestrationService` (4) are the same shape and can ride along or follow.

## Explicitly out of scope

`kinotic-sql`, `kinotic-idl`, and `kinotic-util` have zero Vert.x in their `build.gradle`,
zero `io.vertx` imports, and no project dependencies at all. `kinotic-sql` has no `CrudService`
contact; its `CompletableFuture` usage is its own `StatementExecutor` stack. `kinotic-idl`'s
two references are javadoc.

Leave them alone. The end state is not one async type in the repo — it is two, cleanly
separated by module boundary instead of interleaved on every path from a handler to
Elasticsearch. These three stay Vert.x-free islands nothing converts across. Do not "finish
the job" here.

## The hazard that will not fail to compile

`CompletableFuture` wraps failures propagated through a chain in `CompletionException`; a
direct `completeExceptionally` is not wrapped. Vert.x `Future` always hands the raw cause to
`recover` / `otherwise` / `onFailure`. Every site that inspects, `instanceof`-checks, unwraps,
or formats a `Throwable` can change behavior while still compiling — including log lines and
error responses built from `err.getMessage()`.

It has already leaked into handler code once, with a comment naming it:

```java
// McpJsonRpcHandler.java — a defensive unwrap that becomes dead code after the migration
// a repository failure crosses the strategy's fromCompletionStage wrapped in CompletionException
Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
```

Sweep for these as the last step of each unit and delete the unwraps rather than leaving them.
Add a test wherever a specific exception type drives a response code.

Method mapping is not purely mechanical: `thenApply`→`map`, `thenCompose`→`compose`,
`thenAccept`→`onSuccess`, `exceptionally`→`otherwise`/`recover`, `completedFuture`→
`succeededFuture`, `failedFuture` unchanged. `CompletableFuture.allOf`→`Future.all`, which
returns `CompositeFuture` — use `.map(CompositeFuture::list)` when results are needed; it is
not a drop-in for `allOf`'s `Void`.

## Verifying

The published wire contract does not change: the RPC invoker ships return-value handler
factories for `CompletableFuture`, `Future`, `Mono`, and `Flux`, and `DefaultKinotic` registers
`Future` with the `ReactiveAdapterRegistry`, so `ReactiveTypeConverter` emits the same
`AsyncC3Type<T>` and therefore the same generated TypeScript. Prove it rather than assume it —
generate the C3 schema for one published service before and after and diff.

`kinotic-github` is a useful canary: small, real async chains under test
(`GitHubProjectRepoProvisionerTest`), and it sits between domain contracts above and a
Vert.x-native client below.
