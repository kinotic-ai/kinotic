# kinotic-server

The runnable Spring Boot application that wires together the Kinotic module ecosystem into a deployable server process.

## Overview

`kinotic-server` is the executable entry point for the Kinotic platform. Its primary responsibility is to compose the individual Kinotic library modules — core, domain, persistence, and the RPC gateway — into a single, runnable process. Application developers who want to host Kinotic services locally or in a server environment start here.

The server activates the Kinotic runtime by combining `@SpringBootApplication` with `@EnableKinotic` on `KinoticServerApplication`. `@EnableKinotic` triggers package scanning for all published services and wires the distributed messaging, clustering (Apache Ignite), and async event infrastructure provided by `kinotic-core`.

Security is handled through a pluggable `SecurityService` contract. By default, when OIDC is disabled, a development-only `TemporarySecurityService` is activated that authenticates against a single hardcoded account. When OIDC is enabled via `oidc-security-service.enabled=true`, the `OidcSecurityService` from `kinotic-core` takes over and validates Bearer JWT tokens against one or more configured OIDC providers.

The module ships several Spring profiles (`development`, `clienttest`, `debug`) that enable different configuration overlays. The `clienttest` profile registers a sample `ITestService` / `DefaultTestService` pair that demonstrates the `@Publish` / `@Version` service publication pattern and can be used to verify that a client connection is working end-to-end.

## Key Concepts

- **`@EnableKinotic`** — Annotation placed on the main application class that activates Kinotic's component scanning, service publication infrastructure, and distributed runtime. Only one `@EnableKinotic` annotation should exist per application.
- **`@Publish`** — Marks an interface as a remotely accessible service. The framework derives the service's namespace and name from the package and class name unless overridden. Used in combination with `@Version` to produce a versioned service contract.
- **`@Version`** — Declares the semantic version of a published interface (e.g., `"1.0.0"`). Applied at the type or package level.
- **`SecurityService`** — Core security contract whose single `authenticate(Map<String, String>)` method returns a `CompletableFuture<Participant>`. The framework calls this on every inbound connection attempt. Two implementations ship: `TemporarySecurityService` (development) and `OidcSecurityService` (production).
- **`Participant`** — Represents an authenticated identity. Carries a tenant ID, user ID, participant-type metadata, and a list of granted roles. Sensitive information must not be stored here, as it travels with every RPC request.
- **`ServiceRegistry`** — Provides the ability to programmatically register service instances so they can be invoked remotely, and to obtain dynamic RPC proxy handles for services registered elsewhere on the cluster.
- **`RpcGateway`** — The STOMP-over-WebSocket endpoint (default port `58503`, path `/v1`) that external clients connect to in order to invoke published services and subscribe to event streams.
- **Spring Profiles** — Named configuration slices (`development`, `clienttest`, `debug`) that overlay property files to adjust clustering topology, log verbosity, Elasticsearch connectivity, and which optional beans (e.g., `DefaultTestService`) are active.

## Configuration

Properties for each subsystem are documented in their respective modules (`kinotic-core`, `kinotic-persistence`, `kinotic-rpc-gateway`). The YAML files under `src/main/resources/` set values for those properties appropriate for each Spring profile:

- **`application.yml`** — baseline values applied to all profiles
- **`application-development.yml`** — single-node developer setup: `SHAREDFS` Ignite discovery, local Elasticsearch, relaxed CORS, AI model integration
- **`application-clienttest.yml`** — sets `kinotic.disablePersistence=true` so Elasticsearch is not required during client integration testing
- **`application-debug.yml`** — elevates log levels to `TRACE` for `org.kinotic`, `io.kinotic`, `io.vertx`, and `io.netty`

## Usage Example

Start the server with the `development` profile to run against a local Elasticsearch instance:

```bash
./gradlew :kinotic-server:bootRun --args='--spring.profiles.active=development'
```

To verify the RPC gateway using the built-in test service, activate both the `development` and `clienttest` profiles:

```bash
./gradlew :kinotic-server:bootRun --args='--spring.profiles.active=development,clienttest'
```

A STOMP client can then authenticate and invoke `ITestService`:

```java
// STOMP CONNECT frame headers (development credentials)
Map<String, String> connectHeaders = Map.of(
    "login",    "kinotic",
    "passcode", "kinotic"
);
// After CONNECTED, send a SEND frame to:
// /services/org.kinotic.server.clienttest/ITestService/1.0.0/testMethodWithString
// with a JSON body: ["world"]
// and subscribe to the reply-to destination for the response: "Hello world"
```

## Notes

- `TemporarySecurityService` is for **development only** and provides hardcoded `kinotic`/`kinotic` credentials. It is active whenever `oidc-security-service.enabled` is absent or set to `false`. Do **not** deploy it in production. Enable `OidcSecurityService` by setting `oidc-security-service.enabled=true` and supplying at least one `oidcProviders` entry.
- Authentication supports two credential formats under `TemporarySecurityService`: a `login`/`passcode` field map (STOMP-native), or a standard `Authorization: Basic <base64>` header. The OIDC path requires `Authorization: Bearer <jwt>`.
- The `clienttest` profile deliberately sets `kinotic.disablePersistence=true`, which prevents Elasticsearch from being required during client integration testing. Do not combine `clienttest` with persistence-dependent features.
- `kinotic-rpc-gateway` is conditional on `kinotic.disableRpcGateway` being `false` (the default). Setting it to `true` removes the STOMP server entirely, which is useful when running kinotic-server in a mode where only the HTTP persistence endpoints are needed.
- `@Publish`-annotated interfaces must not be placed on the implementing class; the annotation belongs on the interface only. The framework uses the interface type for service identification and serialization contract resolution.
- JWKS keys fetched from OIDC providers are cached in memory (1-hour TTL) via Caffeine to avoid repeated network round-trips on every authentication. The well-known configuration endpoint is cached for 24 hours.
- The `development` profile configures Ignite `discoveryType: SHAREDFS`, which is suitable for a single developer machine but requires a shared filesystem path to be accessible when running multiple nodes.
