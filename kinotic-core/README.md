# kinotic-core

The foundational module that provides service registration, distributed event routing, RPC proxy generation, and security primitives for the Kinotic platform.

## Overview

Kinotic-core solves the problem of building distributed, reactive microservices that need to communicate transparently across a cluster. Rather than coupling applications to specific transport protocols or messaging formats, it offers a unified programming model: services are registered by name and version, callers obtain typed proxies, and the runtime takes care of serialisation, routing, and back-pressure across nodes.

The module is the single dependency every other Kinotic module builds on. It wires together three infrastructure layers: Apache Ignite (distributed cache and cluster membership), Vert.x (non-blocking event bus, including its Ignite cluster manager bridge), and Project Reactor (reactive return types for `Mono` and `Flux`). Application code interacts only with the higher-level interfaces in `org.kinotic.core.api`; the internal packages handle all lifecycle and transport concerns.

Security is first-class. The `SecurityService` / `SessionManager` pair authenticates callers before any event is dispatched. The module ships an OIDC implementation (`OidcSecurityService`) that validates JWT bearer tokens against one or more configured identity providers, extracts roles from a configurable claim path, and materialises the result as an immutable `Participant` that travels with every inbound request.

Auto-configuration is provided via Spring Boot's service-loader mechanism. Importing `KinoticCoreLibrary` (directly or through the `@EnableKinotic` annotation) activates component scanning, configuration properties, the Ignite node, and the clustered Vert.x instance. Clustering can be disabled entirely with a single property for local development or unit testing.

## Key Concepts

- **CRI (Kinotic Resource Identifier)** — a URI-shaped address with the form `scheme://[scope@]resourceName[/path][#version]`. The two built-in schemes are `srv` (RPC calls routed via the event bus) and `stream` (persistent event streams). All routing decisions inside the runtime are driven by a `CRI`.

- **ServiceIdentifier** — a value object composed of an optional `namespace`, a `name`, an optional `scope`, and a `version`. It canonicalises to a `srv://` CRI and uniquely identifies one registration within the cluster.

- **ServiceRegistry** — the primary entry point for both providers and consumers. Providers call `register(...)` to expose a service object; consumers call `serviceProxy(...)` to receive a dynamic proxy that dispatches calls over the event bus as RPC messages.

- **RpcServiceProxyHandle** — the container returned by `ServiceRegistry.serviceProxy(...)`. It holds the live proxy instance and must be `release()`d when the caller no longer needs it so underlying listeners can be cleaned up.

- **Event / Metadata** — the primitive message type. An `Event<T>` carries a `CRI`, a `Metadata` map of string key-value pairs (content-type, sender, correlation-id, traceparent, etc.), and a typed payload. All inter-node traffic is encoded as `Event<byte[]>`.

- **Participant / Session** — security identity objects. A `Participant` carries an ID, tenant, roles, and metadata; it is deliberately free of sensitive information because it is forwarded to downstream services. A `Session` wraps a `Participant`, enforces CRI-pattern-based send/subscribe authorisation, and tracks the last-used timestamp for timeout eviction.

- **EventBusService / EventStreamService / EventService** — three reactive facades over the underlying transport. `EventBusService` provides non-persistent fire-and-forget or acknowledged delivery with cluster-wide listener monitoring. `EventStreamService` provides persistent, ordered event streams. `EventService` is a smart router that selects the correct backend based on the CRI scheme.

- **ServiceDescriptor / ServiceFunction** — the metadata model that describes a registered service. A `ServiceDescriptor` holds a `ServiceIdentifier` and a collection of `ServiceFunction` entries, each mapping a name to a `java.lang.reflect.Method`. Descriptors can be created reflectively from a plain interface class or assembled programmatically.

## Configuration

### Auto-configuration

Spring Boot's service-loader mechanism discovers `KinoticCoreAutoConfiguration` (in package `org.kinotic.core_autoconfig`) and imports `KinoticCoreLibrary`, which activates `@ComponentScan` and `@EnableConfigurationProperties` for the `org.kinotic.core` package tree.

To activate the module explicitly (e.g., in a library that is not a Spring Boot application), annotate any `@Configuration` class with `@EnableKinotic`.

### `kinotic.*` properties (`KinoticProperties`)

| Property | Type | Default | Description |
|---|---|---|---|
| `kinotic.debug` | `boolean` | `false` | Expose server details and full error messages to callers |
| `kinotic.disableClustering` | `boolean` | `false` | Skip Ignite and use a local-only Vert.x instance |
| `kinotic.eventBusClusterHost` | `String` | `null` | Bind address for the clustered event bus |
| `kinotic.eventBusClusterPort` | `int` | `0` (random) | Port for the clustered event bus |
| `kinotic.eventBusClusterPublicHost` | `String` | `null` | Public hostname when behind a proxy |
| `kinotic.eventBusClusterPublicPort` | `int` | `-1` (same as cluster port) | Public port when behind a proxy |
| `kinotic.maxEventPayloadSize` | `int` | `104857600` (100 MB) | Maximum allowed event payload in bytes |
| `kinotic.maxNumberOfCoresToUse` | `int` | available processors | Upper bound on CPU cores used |
| `kinotic.maxOffHeapMemory` | `long` | Ignite default | Maximum off-heap memory for Ignite caches |
| `kinotic.sessionTimeout` | `long` | `1800000` (30 min) | Session inactivity timeout in milliseconds |
| `kinotic.ignite.discoveryType` | enum | `SHAREDFS` | `LOCAL`, `SHAREDFS`, or `KUBERNETES` |
| `kinotic.ignite.discoveryPort` | `Integer` | `47500` | Ignite TcpDiscoverySpi port |
| `kinotic.ignite.communicationPort` | `Integer` | `47100` | Ignite TcpCommunicationSpi port |
| `kinotic.ignite.localAddress` | `String` | `null` | Network interface for inter-node communication |
| `kinotic.ignite.localAddresses` | `String` | — | Comma-delimited seed addresses for `LOCAL` discovery |
| `kinotic.ignite.sharedFsPath` | `String` | `/tmp/structures-sharedfs` | Shared filesystem path for `SHAREDFS` discovery |
| `kinotic.ignite.workDirectory` | `String` | `/tmp/ignite` | Ignite work directory |
| `kinotic.ignite.kubernetesNamespace` | `String` | `default` | K8s namespace for `KUBERNETES` discovery |
| `kinotic.ignite.kubernetesServiceName` | `String` | `kinotic` | K8s headless service name for `KUBERNETES` discovery |

### `oidc-security-service.*` properties (`OidcSecurityServiceProperties`)

| Property | Type | Default | Description |
|---|---|---|---|
| `oidc-security-service.enabled` | `boolean` | `false` | Activates `OidcSecurityService` |
| `oidc-security-service.tenantIdFieldName` | `String` | `tenantId` | JWT claim used as tenant identifier |
| `oidc-security-service.oidcProviders` | `List<OidcProvider>` | — | One entry per trusted identity provider |
| `oidc-security-service.oidcProviders[n].authority` | `String` | — | Issuer URL (must match `iss` claim exactly) |
| `oidc-security-service.oidcProviders[n].audience` | `String` | — | Expected `aud` claim value |
| `oidc-security-service.oidcProviders[n].domains` | `List<String>` | — | Email domains accepted for this provider |
| `oidc-security-service.oidcProviders[n].rolesClaimPath` | `String` | — | Dot-separated path to the roles list in the JWT (e.g. `realm_access.roles`) |
| `oidc-security-service.oidcProviders[n].roles` | `List<String>` | — | If set, at least one token role must match |
| `oidc-security-service.oidcProviders[n].metadata` | `Map<String,String>` | — | Static metadata merged into every `Participant` from this provider |

## Usage Example

```java
// 1. Activate the module on your Spring Boot application class
@SpringBootApplication
@EnableKinotic
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// 2. Define the service interface — @Publish marks it for automatic registration.
// namespace defaults to the package name; name defaults to the interface name.
@Publish
public interface GreetingService {
    Mono<String> greet(String name);
    Flux<String> streamGreetings(String name);
}

// 3. Implement as a regular Spring @Component — no manual registry interaction needed.
@Component
public class DefaultGreetingService implements GreetingService {
    @Override
    public Mono<String> greet(String name) {
        return Mono.just("Hello, " + name + "!");
    }

    @Override
    public Flux<String> streamGreetings(String name) {
        return Flux.just("Hello", "Hi", "Hey").map(g -> g + ", " + name + "!");
    }
}

// 4. Declare a proxy interface in the consuming module.
// namespace and name must match the @Publish values (or defaults) on the service side.
@Proxy(namespace = "com.example", name = "GreetingService")
public interface GreetingServiceProxy {
    Mono<String> greet(String name);
    Flux<String> streamGreetings(String name);
}

// 5. Inject and use the proxy — Kinotic creates the implementation automatically.
@Component
public class GreetingClient {

    @Autowired
    private GreetingServiceProxy greetingService;

    public void sayHello() {
        greetingService.greet("World").subscribe(System.out::println);
    }
}
```

## Notes

- **Package separation for auto-configuration** — `KinoticCoreAutoConfiguration` lives in `org.kinotic.core_autoconfig` (note the underscore), not in `org.kinotic.core`. This is intentional: the Spring context's component scan starts from `org.kinotic.core`, and placing the auto-configuration class there would cause it to be picked up by the scan in addition to the service-loader mechanism, resulting in duplicate bean registration.

- **Participant is not confidential** — the contract on `Participant` explicitly states that it is forwarded to downstream services that receive RPC calls. Sensitive fields such as passwords or raw tokens must not be stored there.

- **Session CRI authorisation is one-time for temporary sends** — `Session.addTemporarySendAllowed(criPattern)` grants permission to send to a CRI pattern exactly once. After `Session.sendAllowed(...)` returns `true` for that pattern, the pattern is consumed and will not match again. This is used to allow reply-to addresses without permanently widening a session's send permissions.

- **Clustering is required for `EventStreamService`** — persistent event streams are backed by Ignite, so they are unavailable when `kinotic.disableClustering=true`. `EventBusService` degrades gracefully to an in-process Vert.x instance in that mode.

- **RPC serialisation uses `application/json` by default** — `DefaultServiceRegistry.serviceProxy(...)` resolves `application/json` as the content type when none is specified. Custom content-type converters can be registered by implementing `RpcArgumentConverter` and making them available as Spring beans.

- **Vert.x instance startup is synchronous at boot** — `KinoticVertxConfig` calls `buildClustered().toCompletableFuture().get(2, MINUTES)`, blocking the Spring context refresh until the clustered Vert.x instance is fully formed. This guarantees the event bus is ready before any `ApplicationRunner` or `CommandLineRunner` beans execute.

- **Ignite Vert.x cache templates** — `KinoticIgniteConfigCaches` registers two wildcard `CacheConfiguration` beans: `__vertx.*` as REPLICATED and `*` as PARTITIONED with one backup. These mirror the defaults from Vert.x's built-in Ignite configuration and must be present so that both subsystems agree on cache topology.

- **`@EnableKinotic` triggers package scanning** — the annotation is itself annotated with `@KinoticPackage`, which imports `KinoticPackages.Registrar`. The registrar records the annotated class's package so that `@Proxy`-annotated interfaces in that package tree can be discovered and auto-wired as Spring beans by scanners in higher-level modules.

- **`maxNumberOfCoresToUse` is capped at the available processor count** — the setter in `KinoticProperties` silently clamps user-supplied values to `Math.min(availableProcessors, requestedValue)`, so specifying a number larger than the machine's core count has no effect.
