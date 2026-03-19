# kinotic-core — AI Reference

> For narrative documentation see [README.md](./README.md).

## Build & Test Commands

```bash
./gradlew :kinotic-core:build      # compile
./gradlew :kinotic-core:test       # run unit tests
./gradlew :kinotic-core:check      # build + test + lint
```

## Hard Rules

Directives for AI working on this module (sourced from architectural constraints in the Notes section and code):

- Never place auto-configuration classes (e.g., `KinoticCoreAutoConfiguration`) inside the `org.kinotic.core` package tree; they must live in `org.kinotic.core_autoconfig` (underscore separator) to prevent double-registration when Spring component scanning and the service-loader mechanism both run.
- Never store sensitive fields such as passwords or raw tokens on a `Participant`; the contract explicitly forwards `Participant` to downstream services that receive RPC calls.
- Do not use `EventStreamService` (persistent streams) when `kinotic.disableClustering=true`; persistent streams require Ignite and are unavailable in that mode. Use `EventBusService` for local-only deployments.
- Always call `RpcServiceProxyHandle.release()` when a consumer no longer needs a proxy; failing to do so leaks the underlying event bus listeners.
- Do not assume `Session.addTemporarySendAllowed(criPattern)` grants permanent permissions; the permission is consumed on first match and will not fire again. Use permanent send rules for persistent authorisation needs.
- Always use `application/json` as the default content type for RPC serialisation unless a custom `RpcArgumentConverter` Spring bean is explicitly registered; do not assume other formats are available by default.

## Package Structure

| Package | Purpose |
|---|---|
| `org.kinotic.core.api` | Top-level interfaces: `Kinotic`, `ServiceRegistry`, `ServiceDirectory`, `RpcServiceProxy`, `RpcServiceProxyHandle` |
| `org.kinotic.core.api.annotations` | Annotations: `@EnableKinotic`, `@Publish`, `@Proxy`, `@Scope`, `@Version`, `@Alias`, `@KinoticPackage` |
| `org.kinotic.core.api.config` | Spring `@ConfigurationProperties` beans: `KinoticProperties`, `IgniteProperties`, `OidcSecurityServiceProperties`, `OidcProvider` |
| `org.kinotic.core.api.event` | Event model: `CRI`, `Event`, `Metadata`, `EventConstants`, `EventBusService`, `EventStreamService`, `EventService` |
| `org.kinotic.core.api.exceptions` | Exception hierarchy: `KinoticException`, `AuthenticationException`, `AuthorizationException`, `RpcInvocationException`, `RpcMissingServiceException`, `RpcMissingMethodException` |
| `org.kinotic.core.api.security` | Security contracts: `SecurityService`, `SessionManager`, `Session`, `Participant`, `AuthenticationHandler`, `ConnectedInfo` |
| `org.kinotic.core.api.service` | Service metadata model: `ServiceIdentifier`, `ServiceDescriptor`, `ServiceFunction`, `ServiceFunctionInstanceProvider` |
| `org.kinotic.core.internal.api` | Default implementations: `DefaultServiceRegistry`, `DefaultEventService`, `DefaultEventBusService`, `DefaultEventStreamService`, `DefaultSessionManager`, `OidcSecurityService` |
| `org.kinotic.core.internal.api.service.invoker` | Server-side RPC dispatch: `ServiceInvocationSupervisor`, `HandlerMethod`, argument resolvers, return-value converters |
| `org.kinotic.core.internal.api.service.rpc` | Client-side RPC: `DefaultRpcServiceProxyHandle`, argument converters, return-value handler factories for `Mono`, `Flux`, `CompletableFuture` |
| `org.kinotic.core.internal.api.aignite` | Ignite integration helpers: subscription cache listeners, iterator loopers |
| `org.kinotic.core.internal.config` | Spring `@Configuration` classes: `KinoticIgniteBootstrap`, `KinoticVertxConfig`, `KinoticIgniteConfigCaches`, `KinoticRpcIgniteCachesConfig` |
| `org.kinotic.core_autoconfig` | `KinoticCoreAutoConfiguration` — Spring Boot auto-configuration entry point |

## Operation Flow

1. **Startup** — `KinoticIgniteBootstrap` starts an `Ignite` node (unless `kinotic.disableClustering=true`). `KinoticVertxConfig` creates a Vert.x instance; when clustering is enabled, it uses `IgniteClusterManager` so both subsystems share the same cluster topology.

2. **Service registration** — A provider calls `ServiceRegistry.register(serviceIdentifier, MyService.class, instance)`. `DefaultServiceRegistry` creates a `ServiceInvocationSupervisor` that subscribes to the `srv://` CRI on the event bus and begins dispatching inbound `Event<byte[]>` messages to the correct method.

3. **Proxy creation** — A consumer calls `ServiceRegistry.serviceProxy(serviceIdentifier, MyService.class)`. A JDK dynamic proxy is returned that, on each method call, serialises the arguments to JSON, sends an `Event<byte[]>` to the service's CRI, and waits for the reply event on a scoped reply-to address. Return types of `Mono`, `Flux`, and `CompletableFuture` are all supported natively.

4. **Authentication** — Higher-level servers call `SecurityService.authenticate(headers)` to obtain a `Participant`, then use `SessionManager.create(participant, replyToId)` to get a `Session` that enforces per-session CRI authorisation rules.

5. **Event routing** — `EventService` examines the CRI scheme and delegates to either `EventBusService` (non-persistent, Vert.x event bus) or `EventStreamService` (persistent, Ignite-backed streams).

## Public API

| Type | Kind | Description |
|---|---|---|
| `Kinotic` | Interface | Returns `ServerInfo` (node ID and name) for the local node |
| `ServiceRegistry` | Interface | Register/unregister services and create RPC proxies |
| `ServiceDirectory` | Interface | Track service descriptor contracts across the cluster |
| `RpcServiceProxyHandle<T>` | Interface | Holds a live RPC proxy; call `release()` when done |
| `ServiceIdentifier` | Class | Immutable identity (namespace, name, scope, version) for a service |
| `ServiceDescriptor` | Interface | Describes a service: its `ServiceIdentifier` and set of `ServiceFunction`s |
| `ServiceFunction` | Interface | Maps a function name to a `java.lang.reflect.Method` |
| `CRI` | Interface | Kinotic Resource Identifier — factory methods via `CRI.create(...)` |
| `Event<T>` | Interface | Message envelope: CRI + Metadata + payload; factory via `Event.create(...)` |
| `Metadata` | Interface | Mutable string key-value map attached to an `Event` |
| `EventService` | Interface | Route-based facade over `EventBusService` and `EventStreamService` |
| `EventBusService` | Interface | Non-persistent reactive send/listen with listener-status monitoring |
| `EventStreamService` | Interface | Persistent reactive send/listen for ordered event streams |
| `SecurityService` | Interface | Authenticate a caller from header key-value pairs |
| `SessionManager` | Interface | Create, find, and remove `Session` objects |
| `Session` | Interface | Ties a `Participant` to a session ID and enforces CRI-level ACLs |
| `Participant` | Interface | Authenticated identity: ID, tenantId, roles, metadata |
| `AuthenticationHandler` | Class | Vert.x `Handler<RoutingContext>` that calls `SecurityService` and attaches the `Participant` to the routing context |
| `ConnectedInfo` | Class | DTO returned to a newly connected client: participant, replyToId, sessionId |
| `KinoticProperties` | Class | `@ConfigurationProperties(prefix="kinotic")` for all runtime settings |
| `OidcSecurityServiceProperties` | Class | `@ConfigurationProperties(prefix="oidc-security-service")` — OIDC provider list and behaviour |
| `@EnableKinotic` | Annotation | Place on a `@Configuration` class to enable Kinotic package scanning |
| `@Publish` | Annotation | Marks a service interface as remotely accessible |
| `@Proxy` | Annotation | Marks a client interface for automatic proxy creation |
| `@Scope` | Annotation | Designates the scope field on a published service or a proxy method parameter |
| `@Version` | Annotation | Specifies the semantic version of a `@Publish`ed or `@Proxy` interface |
| `@Alias` | Annotation | Defines a human-readable alias for a published service or method |

## Module Dependencies

Kinotic-core has no dependencies on other Kinotic modules; it is the foundation layer.

| Dependency | Reason |
|---|---|
| Apache Ignite (`ignite-core`, `ignite-spring`, `ignite-calcite`, `ignite-kubernetes`, `ignite-slf4j`) | Distributed cache (session store, subscription tracking), cluster membership and discovery |
| Vert.x (`vertx-core`, `vertx-ignite`, `vertx-auth-common`, `vertx-web`, `vertx-web-client`) | Non-blocking event bus used for all RPC and event dispatch; `vertx-ignite` bridges the Vert.x cluster manager to Ignite |
| Project Reactor (`reactor-core`) | Reactive programming model for `Mono` and `Flux` return types throughout the API |
| JJWT (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) | JWT parsing and signature verification in `OidcSecurityService` |
| Jackson (`jackson-core`, `jackson-databind`) | JSON serialisation of RPC arguments and return values |
| Spring Boot / Spring Framework (`spring-boot`, `spring-context`, `spring-aop`, `spring-web`) | Dependency injection, auto-configuration, `ReactiveAdapterRegistry`, `@ConfigurationProperties` |
| OpenTelemetry | Distributed trace context propagation via `traceparent`/`tracestate` headers on events |
| Caffeine | In-process caching of JWKS keys and OIDC well-known configurations |
