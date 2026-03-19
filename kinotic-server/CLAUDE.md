# kinotic-server — AI Reference

> For narrative documentation see [README.md](./README.md).

## Build & Test Commands

```bash
./gradlew :kinotic-server:build      # compile
./gradlew :kinotic-server:test       # run unit tests
./gradlew :kinotic-server:check      # build + test + lint
```

## Hard Rules

Directives for AI working on this module (sourced from architectural constraints in the Notes section and code):

- Never deploy or enable `TemporarySecurityService` in production; it provides hardcoded `kinotic`/`kinotic` credentials and must only be active when `oidc-security-service.enabled` is absent or `false`.
- Always place `@Publish` on the interface, never on the implementing class; the framework uses the interface type for service identification and serialization contract resolution.
- Never combine the `clienttest` profile with persistence-dependent features; that profile explicitly sets `kinotic.disablePersistence=true` to prevent Elasticsearch from being required.
- Do not add a second `@EnableKinotic` annotation; only one must exist per application, and it belongs on `KinoticServerApplication`.
- Always supply at least one `oidcProviders` entry when enabling `OidcSecurityService`; setting `oidc-security-service.enabled=true` without provider configuration will leave authentication unconfigured.
- Do not use the `SHAREDFS` Ignite discovery type (`kinotic.ignite.discoveryType: SHAREDFS`) in multi-node or production deployments; it requires a shared filesystem path accessible to all nodes and is only suitable for a single developer machine.

## Package Structure

```
org.kinotic.server
├── KinoticServerApplication.java   # Entry point: @SpringBootApplication + @EnableKinotic
├── config/
│   └── TemporarySecurityService.java  # Dev-only SecurityService (disabled when OIDC is on)
└── clienttest/
    ├── ITestService.java              # @Publish / @Version("1.0.0") sample interface
    └── DefaultTestService.java        # @Profile("clienttest") implementation
```

## Operation Flow

1. `KinoticServerApplication.main()` boots Spring, which applies `@EnableKinotic` to scan for `@Publish`-annotated service interfaces.
2. `kinotic-core` bootstraps Apache Ignite (distributed cache + cluster) and Vert.x (async event bus).
3. `kinotic-rpc-gateway` starts the STOMP server on port `58503` and begins accepting client connections.
4. On each connection, the gateway calls `SecurityService.authenticate(...)`. `TemporarySecurityService` accepts `login`/`passcode` fields or a `Basic` Authorization header; `OidcSecurityService` requires a `Bearer` JWT.
5. Once authenticated, a `Participant` is created and a session is established. The client can then invoke methods on any registered `@Publish` service.
6. `kinotic-persistence` exposes OpenAPI (port `8080`) and GraphQL (port `4000`) HTTP endpoints backed by Elasticsearch for entity storage and querying.

## Public API

| Class / Interface | Location | Purpose |
|---|---|---|
| `KinoticServerApplication` | `org.kinotic.server` | Main application entry point; annotated with `@SpringBootApplication` and `@EnableKinotic` |
| `TemporarySecurityService` | `org.kinotic.server.config` | Development `SecurityService`; active when `oidc-security-service.enabled` is `false` or absent |
| `ITestService` | `org.kinotic.server.clienttest` | Sample `@Publish` service interface, active under the `clienttest` profile |
| `DefaultTestService` | `org.kinotic.server.clienttest` | `@Profile("clienttest")` implementation of `ITestService` with OpenTelemetry span annotations |
| `SecurityService` | `org.kinotic.core.api.security` | Contract for authentication; implement to replace the default dev credential provider |
| `ServiceRegistry` | `org.kinotic.core.api` | Register and look up services programmatically at runtime |

## Module Dependencies

| Module | Reason |
|---|---|
| `kinotic-core` | Provides `@EnableKinotic`, `SecurityService`, `ServiceRegistry`, Apache Ignite clustering, Vert.x event bus, and all core RPC dispatch infrastructure |
| `kinotic-domain` | Provides domain model types (`Project`, `Application`), CRUD service abstractions, and `ClusterInfoService` |
| `kinotic-persistence` | Provides Elasticsearch-backed entity storage, OpenAPI HTTP endpoints, and GraphQL endpoints |
| `kinotic-rpc-gateway` | Provides the STOMP-over-WebSocket gateway that connects external clients to published services |
| `io.jsonwebtoken:jjwt-*` | JWT parsing and signature verification used by `OidcSecurityService` in `kinotic-core` |
| `com.github.ben-manes.caffeine:caffeine` | In-process caching used by `kinotic-persistence` (e.g., pre-parsed GraphQL document cache and JWKS key cache) |
