# kinotic-orchestrator — AI Reference

> For narrative documentation see [README.md](./README.md).

## Build & Test Commands

```bash
./gradlew :kinotic-orchestrator:build      # compile
./gradlew :kinotic-orchestrator:test       # run unit tests
./gradlew :kinotic-orchestrator:check      # build + test + lint
```

## Hard Rules

Directives for AI working on this module (sourced from architectural constraints in the Notes section and code):

- Never use a non-`GenericApplicationContext` as the host Spring context; `DefaultJobService` casts the application context to `GenericApplicationContext` at startup and will throw a `ClassCastException` if the context is any other type.
- Never automatically close a `CHILD`-scoped `JobDefinition` context; only `ISOLATED`-scoped contexts are closed via `doFinally` — `CHILD` contexts are governed by the parent context lifecycle.
- Always store unnamed primitive/scalar task results under an explicit variable name when using `taskStoreResult`; unnamed primitive results produce only a `WARN` diagnostic and are silently dropped from the context.
- Never store object instances (non-primitives, non-wrappers, non-arrays, non-enums, non-annotations, non-`CharSequence`, non-`Date`, non-`Calendar`) into the `MapPropertySource`; they must be registered as Spring singletons in the bean factory via `taskStoreResult`.
- Always use `ContextUtils.getProperty` or `ContextUtils.getGrindPropertySource` to read values stored by earlier tasks from within a `Task` implementation; calling these outside of a job execution context will throw an `IllegalStateException`.
- Never call `@EnableKinoticGrind`-activated beans before the Spring context is fully started; `JobService` is only available after `DefaultJobService` is registered by `KinoticGrindConfiguration`.

## Package Structure

| Package | Contents |
|---|---|
| `org.kinotic.orchestrator.api` | All public interfaces and value types: `JobService`, `JobDefinition`, `Task`, `Step`, `Result`, `ResultType`, `JobScope`, `ResultOptions`, `Tasks`, `Progress`, `Diagnostic`, `DiagnosticLevel`, `StepInfo`, `ContextUtils`, `HasSteps` |
| `org.kinotic.orchestrator.api.annotations` | `@EnableKinoticGrind` — the Spring Boot activation annotation |
| `org.kinotic.orchestrator.internal.api` | Runtime implementations: `DefaultJobService`, `DefaultJobDefinition`, `TaskStep`, `JobDefinitionStep`, `AbstractStep`, `AbstractTask`, `InstanceTask`, `ClassTask`, `ValueTask`, `NoopTask`, `DefaultResult`, `GrindConstants` |
| `org.kinotic.orchestrator.internal.api.config` | `KinoticGrindConfiguration` — Spring `@Configuration` that component-scans the internal package |

## Operation Flow

1. A caller builds a `JobDefinition` tree using the fluent API and calls `JobService.assemble(jobDefinition)`.
2. `DefaultJobService` wraps the whole definition in a root `JobDefinitionStep` inside a `Flux.defer(...)`, so assembly is lazy.
3. When the caller subscribes, `JobDefinitionStep.assemble(...)` creates or resolves the appropriate `GenericApplicationContext` based on `JobScope`, then iterates over each `Step`.
4. For sequential jobs, steps are chained with `Flux.concat`; for parallel jobs they are merged with `Flux.merge` on `Schedulers.parallel()`.
5. Each `TaskStep.assemble(...)` executes the `Task`, inspects the return value, and emits one or more `Result` events. If the task returns another `Task` or a `JobDefinition`, a `DYNAMIC_STEPS` result is emitted first, followed by the results from the dynamically expanded work.
6. If `storeResult` is set, object results are registered as singletons in the execution `ApplicationContext`; primitive/scalar results are stored in a `MapPropertySource` named `__grindJobContext`, making them injectable via `@Value` in subsequent tasks.
7. All `Result` events bubble up through ancestor `StepInfo` chains so subscribers can correlate any emission back to its position in the original tree.

## Public API

| Class / Interface | Role |
|---|---|
| `JobService` | Entry point. `assemble(JobDefinition)` and `assemble(JobDefinition, ResultOptions)` return `Flux<Result<?>>`. |
| `JobDefinition` | Fluent builder for a unit of work. Static factory: `JobDefinition.create(...)`. |
| `Task<T>` | Interface for a single executable operation. Implement directly or use `Tasks` helpers. |
| `Tasks` | Static factory: `fromCallable`, `fromSupplier`, `fromRunnable`, `fromValue`, `fromExec`, `noop`, `transformResult`. |
| `Result<T>` | Single stream emission. Provides `getResultType()`, `getValue()`, `getStepInfo()`. |
| `ResultType` | Enum: `VALUE`, `NOOP`, `PROGRESS`, `DIAGNOSTIC`, `DYNAMIC_STEPS`, `EXCEPTION`. |
| `JobScope` | Enum: `CHILD`, `PARENT`, `ISOLATED`. |
| `ResultOptions` | Controls which result types appear in the stream (`diagnosticsLevel`, `enableProgressResults`). |
| `DiagnosticLevel` | Enum: `NONE`, `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`. |
| `Progress` | Value object emitted with `ResultType.PROGRESS`: `percentageComplete` (int) + `message` (String). |
| `Diagnostic` | Value object emitted with `ResultType.DIAGNOSTIC`: `diagnosticLevel` + `message`. |
| `StepInfo` | Linked structure identifying the sequence path from a root step to the emitting step. |
| `ContextUtils` | Helper for reading values out of the active execution context: `getProperty(name, ctx)`, `getGrindPropertySource(ctx)`. |
| `@EnableKinoticGrind` | Spring Boot activation annotation. Place on the `@SpringBootApplication` class. |

## Module Dependencies

| Dependency | Reason |
|---|---|
| `kinotic-core` | Provides shared Kinotic infrastructure and the `@EnableKinotic` foundation used by host applications. |
| `io.projectreactor:reactor-core` | Reactive stream execution via `Flux` and `FluxSink`. |
| `org.reactivestreams:reactive-streams` | Standard reactive-streams API; used in `Step.assemble` return type (`Publisher<Result<?>>`). |
| `commons-io:commons-io` | Used in `Tasks.fromExec` to read process output streams. |
| Spring Framework (transitive) | `GenericApplicationContext`, `AnnotationConfigApplicationContext`, `AutowireCapableBeanFactory`, `MapPropertySource`. |
