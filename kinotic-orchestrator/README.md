# kinotic-orchestrator

A reactive, Spring-integrated task orchestration library that composes units of work into a `Flux<Result<?>>` stream.

## Overview

Many applications need to execute sequences (or parallel groups) of discrete operations, track their progress, pass data between them, and surface errors without blocking. Traditional approaches either block the calling thread, scatter state across ad-hoc fields, or couple execution logic tightly to the caller. kinotic-orchestrator solves this by modeling work as a tree of `Task` and `JobDefinition` objects that are assembled, lazily, into a Project Reactor `Flux`. Subscribing to that `Flux` drives execution; unsubscribing cancels it.

Each `JobDefinition` is backed by a Spring `GenericApplicationContext` whose lifetime is scoped to that job. Tasks within the same job can store results into that context and have subsequently-executed tasks autowired with those results, enabling lightweight dependency injection between pipeline steps without any external state management.

Within the broader Kinotic architecture, kinotic-orchestrator depends only on kinotic-core and serves as a self-contained execution layer. Higher-level modules can build workflows by composing `JobDefinition` trees and consuming the resulting event stream, while remaining decoupled from the internal scheduling and context-management mechanics.

The library is activated with a single Spring Boot annotation, `@EnableKinoticGrind`, which imports the auto-configuration that registers the `JobService` bean. No additional infrastructure—no message broker, no database, no scheduler daemon—is required.

## Key Concepts

- **`JobDefinition`** — A named, ordered (or parallel) collection of `Task` instances and nested `JobDefinition` instances. It declares a `JobScope` that controls whether its execution context is a child of the parent context, shares the parent context, or is fully isolated. Created via `JobDefinition.create(...)` static factory methods.

- **`Task<T>`** — The unit of work. Implementations receive a `GenericApplicationContext` and return a value, another `Task`, a `JobDefinition`, or a reactive `Publisher`. Returning a `Task` or `JobDefinition` causes dynamic step expansion at runtime.

- **`Tasks`** — A factory class providing static helpers to adapt common Java constructs into `Task` instances: `fromCallable`, `fromSupplier`, `fromRunnable`, `fromValue`, `fromExec`, `noop`, and `transformResult`.

- **`Step`** — The internal execution unit assembled from a `Task` or a nested `JobDefinition`. Each `Step` carries a `sequence` number and produces a `Publisher<Result<?>>` when assembled.

- **`Result<T>`** — A single emission on the output `Flux`. Carries a `ResultType`, a typed `getValue()`, and a `StepInfo` chain that tracks which sequence of steps produced it.

- **`ResultType`** — Enum classifying each emitted `Result`: `VALUE` (final task output), `NOOP` (task was intentionally skipped), `PROGRESS` (percentage-complete notification), `DIAGNOSTIC` (structured log message), `DYNAMIC_STEPS` (a task returned a new `Task` or `JobDefinition` at runtime), or `EXCEPTION` (error detail when progress notifications are enabled).

- **`JobScope`** — Enum controlling the `ApplicationContext` used for a `JobDefinition`: `CHILD` (new child context, default), `PARENT` (reuse the parent context), or `ISOLATED` (new root context, closed on completion).

- **`ResultOptions`** — Configuration passed to `JobService.assemble` controlling which `ResultType` values appear in the stream. `DiagnosticsLevel` selects the minimum severity of `DIAGNOSTIC` results; `enableProgressResults` gates `PROGRESS` and `EXCEPTION` stream events.

## Configuration

### Activating the module

Add `@EnableKinoticGrind` to any Spring Boot application class. This imports `KinoticGrindConfiguration`, which component-scans `org.kinotic.orchestrator.internal` and registers `DefaultJobService` as a Spring bean.

```java
@SpringBootApplication
@EnableKinoticGrind
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### Auto-configuration class

| Class | Purpose |
|---|---|
| `KinoticGrindConfiguration` | Imported by `@EnableKinoticGrind`. Triggers component scan of the internal package tree. No external properties file is required. |

### Internal property source

The key `__grindJobContext` names the `MapPropertySource` that kinotic-orchestrator adds to each execution context. Tasks that call `taskStoreResult(task, "variableName")` write scalar results there, making them accessible via `@Value("${variableName}")` in subsequent tasks within the same scope.

## Usage Example

The example below builds a two-step pipeline. The first task creates a configuration object and stores it in the execution context. The second task is autowired with that object and uses it to produce the final string result. Both `VALUE` results are emitted on the returned `Flux`.

```java
@SpringBootApplication
@EnableKinoticGrind
public class ExampleApp implements CommandLineRunner {

    @Autowired
    private JobService jobService;

    @Override
    public void run(String... args) {
        JobDefinition job = JobDefinition.create("example-pipeline")
            .taskStoreResult(
                Tasks.fromCallable("build-config", () -> new AppConfig("prod")))
            .task(
                Tasks.fromCallable("run-job", new Callable<String>() {
                    @Autowired
                    private AppConfig appConfig;

                    @Override
                    public String call() {
                        return "Running in environment: " + appConfig.getEnv();
                    }
                }));

        jobService.assemble(job)
                  .filter(r -> r.getResultType() == ResultType.VALUE)
                  .subscribe(r -> System.out.println("Result: " + r.getValue()));
    }
}
```

To receive progress and diagnostic events alongside value results, pass a `ResultOptions` instance:

```java
ResultOptions options = new ResultOptions()
    .setDiagnosticsLevel(DiagnosticLevel.INFO)
    .setEnableProgressResults(true);

jobService.assemble(job, options)
          .subscribe(r -> System.out.println(r.getResultType() + ": " + r.getValue()));
```

## Notes

- `JobDefinition.create()` without a description assigns a random UUID as the description. Providing an explicit description improves log readability, since the description string appears in every progress and diagnostic message emitted by that job.

- `DefaultJobService` requires the host `ApplicationContext` to be a `GenericApplicationContext`. Applications that use a non-generic context type will fail at startup with a `ClassCastException` inside `setApplicationContext`.

- When `JobScope.ISOLATED` is used, the child `AnnotationConfigApplicationContext` is closed via `doFinally` after the job completes or errors. When `JobScope.CHILD` is used the child context is created but **not** closed automatically; the parent context lifecycle governs cleanup.

- Task results stored via `taskStoreResult` follow different storage paths depending on the value type. Object instances that are not primitives, wrappers, arrays, enums, annotations, `CharSequence`, `Date`, or `Calendar` are registered as Spring singletons in the bean factory. All other types are stored as `MapPropertySource` entries and must be given an explicit variable name; unnamed primitive results produce a `WARN` diagnostic and are not stored.

- When a `Task` returns a reactive type (anything recognised by Spring's `ReactiveAdapterRegistry`), the `TaskStep` adapts it to a `Publisher` and does not emit a `VALUE` result until that publisher completes. This means a single `Task` can legally emit multiple `VALUE` results by returning a `Flux`.

- Parallel `JobDefinition` execution uses `Flux.merge` followed by `.parallel().runOn(Schedulers.parallel()).sequential()`. The merge happens before the parallel scheduler, so all publishers are subscribed concurrently but results are serialised back onto a single thread before being forwarded to the parent sink.

- `ContextUtils.getProperty` and `ContextUtils.getGrindPropertySource` are the supported way for `Task` implementations to read values stored by earlier tasks without relying on Spring `@Value` injection. They both require the `__grindJobContext` property source to be present; calling them outside of a job execution context will throw an `IllegalStateException`.
