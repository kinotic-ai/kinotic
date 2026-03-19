# kinotic-rpc-gateway — AI Reference

> For narrative documentation see [README.md](./README.md).

## Build & Test Commands

```bash
./gradlew :kinotic-rpc-gateway:build      # compile
./gradlew :kinotic-rpc-gateway:test       # run unit tests
./gradlew :kinotic-rpc-gateway:check      # build + test + lint
```

## Hard Rules

Directives for AI working on this module (sourced from architectural constraints in the Notes section and code):

- Never implement business logic inside the gateway; it is a thin transport adapter that translates between STOMP wire format and the internal `Event<byte[]>` / CRI model — all domain logic belongs in services registered on the event bus.
- Always enforce reply-to security on inbound `srv://` SEND frames: the scope portion of the `reply-to` CRI must exactly match the authenticated participant's `replyToId`, and wildcard characters in `reply-to` addresses must be unconditionally rejected.
- Never allow the `KinoticRpcGatewayAutoConfiguration` class to reside inside the main `org.kinotic.rpc.gateway` package — it must remain in the sibling package `org.kinotic.rpc.gateway_autoconfig` to prevent Spring component scanning from activating it outside of the explicit `AutoConfiguration.imports` entry.
- Do not add constructor arguments to `DefaultStompServerHandler`; use the `Services` facade to introduce new collaborators so that handler constructors remain short and stable.
- Always pause the `StompServerConnection` while processing a SEND frame and resume it only after the downstream send completes, to maintain transport-level back-pressure.
- Never change the `HFTQueueManager` interface or `HftRawEvent` serialisation contract (`RAW_EVENT_FORMAT_STOMPISH`) without coordinating with any production Chronicle Queue implementation that may be wired in.

## Package Structure

| Package | Responsibility |
|---|---|
| `org.kinotic.rpc.gateway.api.config` | Public configuration types: `KinoticRpcGatewayProperties` and `RpcGatewayProperties`. |
| `org.kinotic.rpc.gateway.internal.config` | Internal Spring `@Configuration` that exposes `RpcGatewayProperties` as a standalone bean. |
| `org.kinotic.rpc.gateway.internal.endpoints` | Core connection lifecycle: `RpcGatewayEndpointInitializer`, `RpcGatewayVertcleFactory`, `EndpointConnectionHandler`, and the `Services` facade. |
| `org.kinotic.rpc.gateway.internal.endpoints.stomp` | STOMP-specific layer: `DefaultStompServerHandler`, `DefaultStompServerHandlerFactory`, `FrameEventAdapter`, `StompSubscriptionEventSubscriber`, and `GatewayUtils`. |
| `org.kinotic.rpc.gateway.internal.api.security` | `CliSecurityService` — a `SecurityService` decorator that short-circuits authentication for CLI participants. |
| `org.kinotic.rpc.gateway.internal.hft` | `HFTQueueManager` interface, `DefaultHFTQueueManager` no-op implementation, and `HftRawEvent` data container. |
| `org.kinotic.rpc.gateway_autoconfig` | `KinoticRpcGatewayAutoConfiguration` — the Spring Boot auto-configuration entry point, intentionally in a separate package to avoid component scanning. |

## Operation Flow

1. On startup, `RpcGatewayEndpointInitializer` (`@PostConstruct`) asks `RpcGatewayVertcleFactory` to create one `StompServerVerticle` per configured core and deploys them all to the shared Vert.x instance.
2. Each verticle listens for incoming WebSocket upgrades on port `58503` at path `/v1` (STOMP sub-protocol `v12.stomp`).
3. When a client connects, `DefaultStompServerHandlerFactory` creates a new `DefaultStompServerHandler` (and, transitively, a new `EndpointConnectionHandler`) scoped to that connection.
4. On CONNECT, `EndpointConnectionHandler.authenticate` either resumes an existing `Session` (if a `session` header is present) or calls `SecurityService.authenticate` to create a new one. The server sends a CONNECTED frame back containing a `connected-info` header with the participant, session ID, and reply-to ID encoded as JSON.
5. On SEND, the STOMP frame is wrapped as a `FrameEventAdapter` and dispatched to `EventBusService.sendWithAck` (for `srv://`) or `EventStreamService.send` (for `stream://`). The connection is paused until the send completes to provide back-pressure.
6. On SUBSCRIBE, `EndpointConnectionHandler.subscribe` attaches a `StompSubscriptionEventSubscriber` to `EventBusService.listen` or `EventStreamService.listen`. Incoming events are serialized to STOMP MESSAGE frames and written directly to the connection.
7. On DISCONNECT, the `Session` is removed immediately. On connection close without DISCONNECT, subscriptions are cancelled but the session is preserved unless `disable-sticky-session` was requested.

## Public API

| Class / Interface | Package | Purpose |
|---|---|---|
| `KinoticRpcGatewayProperties` | `org.kinotic.rpc.gateway.api.config` | Top-level `@ConfigurationProperties` bean; bind via `kinotic.*` to control the gateway. |
| `RpcGatewayProperties` | `org.kinotic.rpc.gateway.api.config` | Nested configuration for STOMP server options (port, WebSocket path, body size) and CLI connections toggle. |
| `HFTQueueManager` | `org.kinotic.rpc.gateway.internal.hft` | Interface for plugging in a high-frequency persistent event queue. Provide a bean implementing this interface to replace the no-op default. |

## Module Dependencies

| Dependency | Reason |
|---|---|
| `kinotic-core` | Provides `CRI`, `Event`, `EventConstants`, `EventBusService`, `EventStreamService`, `SecurityService`, `SessionManager`, `KinoticProperties`, `ExceptionConverter`, and all security model types that the gateway coordinates. |
| `kinotic-domain` | Domain model types used across the Kinotic platform. |
| `org.kinotic:vertx-stomp-lite` | Lightweight STOMP 1.2 server implementation built on Vert.x, providing `StompServerVerticle`, `StompServerHandler`, `StompServerConnection`, and frame parsing. |
| `io.vertx:vertx-core` | Reactive, non-blocking I/O runtime; supplies the `Vertx` instance, event loop, and `DeploymentOptions`. |
| `io.vertx:vertx-web` | Provides `Router` and `StaticHandler` used when assembling the HTTP server that hosts the WebSocket endpoint. |
| `io.projectreactor:reactor-core` | Reactive operator support (`Mono`, `BaseSubscriber`) used throughout connection and subscription handling. |
| `tools.jackson.core:jackson-databind` | Serialises `ConnectedInfo` and `Participant` objects to JSON for the `connected-info` response header and `sender` event header. |
| `org.apache.ignite:ignite-core` | Cluster-level distributed data structures used by `SessionManager` and the event bus (provided via `kinotic-core`). |
