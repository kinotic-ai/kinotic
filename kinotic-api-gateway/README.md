# kinotic-rpc-gateway

A Spring Boot auto-configured WebSocket/STOMP gateway that bridges external clients to the Kinotic internal event bus and stream services.

## Overview

Modern distributed applications need a well-defined entry point through which external clients — browsers, mobile apps, CLI tools, and edge devices — can invoke services and subscribe to event streams running inside a server cluster. Without a dedicated gateway, each subsystem must independently manage connection lifecycle, authentication, session state, and protocol translation, resulting in fragmented and difficult-to-maintain code.

`kinotic-rpc-gateway` solves this by providing a single, self-contained entry point. It accepts inbound WebSocket connections carrying the STOMP 1.2 protocol, authenticates each connecting participant via the Kinotic `SecurityService`, and then routes STOMP SEND and SUBSCRIBE frames onto the appropriate Kinotic event bus (`EventBusService`) or stream (`EventStreamService`) destination. Responses and events flow in the reverse direction: internal bus messages are serialized back into STOMP MESSAGE frames and written to the open client connection.

The gateway plugs into the broader Kinotic architecture as a thin transport adapter. It does not implement any business logic of its own; instead, it translates between the STOMP wire format and the internal `Event<byte[]>` / CRI model provided by `kinotic-core`, so that services registered on the event bus are automatically reachable from any STOMP-capable client without modification.

Session handling is a first-class concern. After a successful CONNECT handshake, the gateway establishes or resumes a `Session`, periodically touches it to prevent expiry, and differentiates between an intentional DISCONNECT (session is removed immediately) and a network-level connection drop (session is preserved for reconnection, allowing a client to rejoin using its session identifier).

## Key Concepts

- **CRI (Kinotic Resource Identifier)** — A URI-shaped address used to route events, of the form `scheme://[scope@]resourceName[/path][#version]`. The gateway recognises two schemes: `srv://` for point-to-point RPC-style service invocations and `stream://` for event stream subscriptions.

- **STOMP frame / `Event<byte[]>` duality** — Inbound STOMP SEND frames are wrapped by `FrameEventAdapter` into `Event<byte[]>` objects without copying headers, giving the rest of the system a scheme-agnostic view of the request. Outbound internal events are converted back to STOMP MESSAGE frames by `GatewayUtils.eventToStompFrame`.

- **Sticky session** — By default, when a client's network connection closes without a DISCONNECT frame, the gateway retains the authenticated `Session` (and its associated subscriptions timer) so the client can reconnect using the same session ID. Clients that opt out by sending the `disable-sticky-session` header receive a stateless connection whose session is torn down on any closure.

- **Reply-to scoping** — For `srv://` requests the gateway enforces that any `reply-to` header is scoped to the authenticated participant's `replyToId`, preventing one participant from directing replies into another participant's address space. Wildcard characters in `reply-to` addresses are unconditionally rejected.

- **CLI participant** — When `enableCLIConnections` is `true` (the default), a client that connects with the well-known `login` value of the CLI participant ID is granted a pre-authenticated `DefaultParticipant` with the `ANONYMOUS` role, bypassing the normal `SecurityService`. All other logins fall through to the configured `SecurityService` delegate.

- **`HFTQueueManager`** — An interface reserved for optional high-frequency-trading (low-latency) persistent queue integration. The default implementation (`DefaultHFTQueueManager`) is a no-op placeholder; the interface exists so a production implementation backed by a Chronicle Queue can be wired in without changing the gateway internals.

- **Verticle-per-core deployment** — The STOMP server runs as one or more Vert.x `StompServerVerticle` instances. The count is taken from `kinotic.maxNumberOfCoresToUse`, so the gateway scales horizontally within a single JVM by pinning one event loop per available core.

- **`Services` facade** — A Spring `@Component` that aggregates all collaborators (`EventBusService`, `EventStreamService`, `SecurityService`, `SessionManager`, `ExceptionConverter`, `JsonMapper`, `Vertx`, and the gateway properties) into a single injectable object. This keeps handler constructors short and makes adding new dependencies a one-field change.

## Configuration

### Auto-configuration

The module registers `KinoticRpcGatewayAutoConfiguration` in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. That class imports `KinoticRpcGatewayLibrary`, which carries the `@ComponentScan` and `@EnableConfigurationProperties` annotations. The entire module is conditional on the property:

```
kinotic.disableRpcGateway=false   # default — gateway is active
```

Set `kinotic.disableRpcGateway=true` to prevent the gateway from starting.

### Spring properties

All properties are nested under the `kinotic` prefix (from `KinoticProperties` and `KinoticRpcGatewayProperties`).

| Property | Type | Default | Description |
|---|---|---|---|
| `kinotic.disableRpcGateway` | `boolean` | `false` | Set to `true` to disable all gateway beans. |
| `kinotic.rpcGateway.stomp.port` | `int` | `58503` | TCP port the STOMP WebSocket server listens on. |
| `kinotic.rpcGateway.stomp.websocketPath` | `String` | `/v1` | HTTP path used for the WebSocket upgrade. |
| `kinotic.rpcGateway.stomp.debugEnabled` | `boolean` | inherits `kinotic.debug` | When `true`, the STOMP server emits additional diagnostic output. |
| `kinotic.rpcGateway.stomp.maxBodyLength` | `int` | inherits `kinotic.maxEventPayloadSize` (100 MB) | Maximum inbound frame body size in bytes. |
| `kinotic.rpcGateway.enableCLIConnections` | `boolean` | `true` | Allow connections using the CLI participant ID to bypass normal authentication. |
| `kinotic.maxNumberOfCoresToUse` | `int` | all available CPUs | Controls how many STOMP server verticles are deployed. |
| `kinotic.sessionTimeout` | `long` (ms) | `1800000` (30 min) | Idle session expiry; the gateway refreshes the session at half this interval. |
| `kinotic.maxEventPayloadSize` | `int` | `104857600` (100 MB) | Maximum WebSocket frame size and STOMP body limit. |
| `kinotic.debug` | `boolean` | `false` | Enables verbose error details returned to clients. |

## Usage Example

The following snippet shows how to embed the gateway in a Spring Boot application and override the default STOMP port programmatically before the context starts.

```java
import org.kinotic.api.gateway.api.config.KinoticApiGatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MyApplication.class);
        ConfigurableApplicationContext ctx = app.run(args);

        // Log the active STOMP port after startup
        KinoticRpcGatewayProperties props =
                ctx.getBean(KinoticRpcGatewayProperties.class);
        int port = props.getRpcGateway().getStomp().getPort();
        System.out.println("RPC Gateway listening on port " + port);
    }
}
```

To connect from a STOMP client (e.g., a browser using the `@stomp/stompjs` library), open a WebSocket to `ws://host:58503/v1`, negotiate the `v12.stomp` sub-protocol, and send a CONNECT frame with `login` / `passcode` credentials. On success the server returns a CONNECTED frame carrying a `connected-info` JSON header with the assigned session ID and reply-to address.

## Notes

- The `DefaultStompServerHandlerFactory` creates a new `DefaultStompServerHandler` (and `EndpointConnectionHandler`) for every inbound STOMP connection. State such as the authenticated `Session` and the active subscription map is therefore per-connection and requires no external locking.

- The gateway pauses the underlying `StompServerConnection` while processing each SEND frame and resumes it only after the downstream send completes. This provides back-pressure at the transport level but means that a slow downstream service will cause the client's SEND pipeline to stall until the previous frame is acknowledged.

- The `KinoticRpcGatewayAutoConfiguration` class lives in the package `org.kinotic.rpc.gateway_autoconfig` (a sibling of the main `org.kinotic.rpc.gateway` package) specifically to prevent it from being picked up by Spring's component scan. Only the explicit entry in the `AutoConfiguration.imports` file activates it.

- The `Services` facade bean is a `@Component` with `@Autowired` fields rather than constructor injection. This pattern was chosen to keep the `DefaultStompServerHandler` constructor argument list short while allowing the set of collaborators to grow without a constructor change. `Services` is a framework-internal type and is not part of the public API.

- Reply-to security for `srv://` requests is enforced on the inbound SEND path: the scope portion of the `reply-to` CRI must exactly match the authenticated participant's `replyToId`. This prevents a participant from injecting replies into another participant's address space. Wildcard characters are an unconditional error.

- The WebSocket server also serves static files from the `continuum-gateway-static` resource root via a Vert.x `StaticHandler`. This allows a bundled diagnostic UI to be served from the same port as the STOMP endpoint.

- `HFTQueueManager` is present as a named interface and no-op default to support future integration of a persistent, low-latency event queue. The interface and `HftRawEvent` data type define the serialisation contract (`RAW_EVENT_FORMAT_STOMPISH`) that any future implementation must honour.
