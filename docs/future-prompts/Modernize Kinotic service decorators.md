# Modernize Kinotic service decorators: legacy (experimentalDecorators) → TC39 standard

## Objective
Rewrite the service-registration decorators in `@kinotic-ai/core` — `@Publish`,
`@Version`, `@Scope`, `@Context` — from TypeScript **legacy (experimental)**
decorators to **TC39 standard** decorators, and update the RPC dispatch + every
consumer so published services keep working. Goal: one decorator system that runs
on **both** Bun (backend/CLI) **and** Oxc-based bundlers, so users can build a
modern Vue/Vite app that publishes services (Vite ≥7/8 uses Oxc, which supports
only standard decorators — not the legacy ones Kinotic uses today).

## Background / why
- TS has two decorator systems: **legacy** (`experimentalDecorators: true`,
  `reflect-metadata`, old signatures) and **standard** (TC39 Stage 3, default in
  TS 5.0+, `(value, context)` signatures, `context.metadata`).
- The modern toolchain (Oxc/Rolldown → Vite 7/8; also SWC) implements only the
  standard decorators. Kinotic's are legacy, so a modern Vite/Oxc app that
  `@Publish`es a service fails to transpile (emits a raw `@Publish` → SyntaxError).
- Bun (workspace runtime + bunup builds) DOES support standard decorators —
  verified on Bun 1.3.11: a standard class decorator returning a registering
  subclass runs, and `context.metadata` works during decoration. **Re-verify on
  the installed Bun**: write a tiny standalone `.ts` with a standard class
  decorator + `context.metadata`, `bun run` it, confirm it registers + reads
  metadata, and check whether `SomeClass[Symbol.metadata]` is readable at runtime
  (it was `undefined` on 1.3.11 — see "metadata-at-runtime" below).

## Current state (re-read/re-grep to confirm — don't trust this blindly)
File: `kinotic-js/workspace/packages/core/src/api/KinoticDecorators.ts`
- `@Publish(namespace, name?)` — **class** decorator. Returns a replacement
  constructor that, on each `new`, builds a `ServiceIdentifier`
  (namespace/name/version/scope) and calls
  `Kinotic.serviceRegistry.register(serviceIdentifier, instance)`. Reads `@Version`
  + `@Scope` via `Reflect.getMetadata`.
- `@Version(v)` — **class** decorator; semver-validates; `Reflect.defineMetadata`.
- `@Scope` — **field** decorator; records a property name; `@Publish` reads that
  property's value off each instance at construction (calls it if it's a function).
- `@Context()` — **parameter** decorator; records which method params get the
  invocation context.
- Runtime read: `internal/api/ServiceInvocationSupervisor.ts` does
  `Reflect.getMetadata(CONTEXT_METADATA_KEY, instance, methodName)` at dispatch to
  inject context into the call.
- Core uses **custom metadata keys only — NOT `emitDecoratorMetadata`/`design:type`**
  (confirmed). That's the one legacy feature with no standard equivalent, and you
  don't depend on it. Good.

## Legacy → standard mapping
- `@Publish` (class): `(value, context: ClassDecoratorContext)` returning
  `class extends value { constructor(...a){ super(...a); register(...) } }`. Read
  `@Version`/`@Scope` from the **shared `context.metadata`** during decoration
  (all decorators on a class + its members share one metadata object) — no
  reflect-metadata needed.
- `@Version` (class): `(value, ctx) => { ctx.metadata.version = v }`.
- `@Scope` (field): `(value, ctx: ClassFieldDecoratorContext)` → record the field
  name into `ctx.metadata`; `@Publish` reads it during decoration and reads the
  instance value in the subclass constructor.
- `@Context` (**parameter — the hard part**): TC39 has **no parameter decorators**.
  This needs a redesign. **Propose options and get sign-off before building**, e.g.
  a *method* decorator taking arg positions (`@WithContext(0,2) m(...)`), or move
  context injection off decorators (explicit param / `getContext()` in-body).

## Metadata-at-runtime (key design point)
`SomeClass[Symbol.metadata]` may be `undefined` at runtime (it was on Bun 1.3.11),
so do NOT rely on reading metadata off a finished class later. Instead **capture
what the RPC layer needs at decoration time** into a registry (you already have
`Kinotic.serviceRegistry`) — record `@Context` param positions per (service,
method) when decorated, and have `ServiceInvocationSupervisor` look them up there
instead of reading off the instance. (A `Symbol.metadata` polyfill is the
fallback, but capture-at-decoration is cleaner and engine-independent.)

## This is a breaking change — plan the rollout
Standard semantics break anyone compiling these with `experimentalDecorators: true`:
- Warrants a **major `@kinotic-ai/core` bump** (e.g. 2.0.0).
- **Every site applying these decorators must switch to standard** (remove/disable
  `experimentalDecorators`): the **CLI** (`kinotic-js/kinotic-cli`, builds with
  `tsc`), **load-generator**, **e2e-tests**, backend-TS services, and **user**
  service code (this is a user-facing platform change — document it).
- Present a clear versioning/rollout plan and **confirm before flipping consumers**.
  Prove the rewritten decorators + dispatch in isolation (core + tests) first, then
  coordinate the consumer flip; don't push a half-flipped, broken-consumer state.

## Approach
1. Re-confirm the decorators + `ServiceInvocationSupervisor` usage and the installed
   Bun's standard-decorator support (small empirical test).
2. Propose the design — especially the `@Context` replacement and the
   capture-at-decoration registry — and get sign-off.
3. Rewrite `KinoticDecorators.ts`; update `ServiceInvocationSupervisor`.
4. Update core tests (`packages/core/test/` + internal RPC tests) to run under
   standard decorators; pass on Bun AND through an Oxc/esbuild build.
5. Flip consumers' tsconfigs + any decorator usage; build each; run a full RPC
   round-trip (a `@Publish`'d service invoked via proxy, with `@Context` injection).
6. Major-bump core; note the re-publish / re-consume chain.

## Constraints / conventions
- `kinotic-js/workspace` uses **bun** (NOT pnpm/npm). Per repo CLAUDE.md: verify
  decorator behavior empirically, not by name; one top-level type per file; comments
  at the right altitude. These decorators are **published API** — treat as a
  coordinated breaking release.

## Branch
Confirm the target branch first (likely a feature branch off `develop`). Commit
incrementally.
