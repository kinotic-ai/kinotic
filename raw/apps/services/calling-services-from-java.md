---
title: "Calling Services from Java"
description: "How a Java program outside a Kinotic cluster connects to a gateway and calls services through typed proxies."
canonical_url: "https://kinotic.ai/apps/services/calling-services-from-java"
---
# Calling Services from Java

> How a Java program outside a Kinotic cluster connects to a gateway and calls services through typed proxies.

A Java program that is a member of a Kinotic cluster calls services through the proxies its `ServiceRegistry`
hands out, over the cluster's own event bus. A Java program outside the cluster, such as a service of one
installation calling a service of another, uses `kinotic-client`, the Java client of Kinotic RPC: it connects
to a gateway over STOMP on a WebSocket, the protocol the TypeScript client speaks, and hands out the same typed
proxies, so a service interface is called the same way from either side.

## Adding the client

```groovy
implementation 'org.kinotic:kinotic-client:<version>'
```

The client is part of Kinotic RPC, licensed under the Apache License 2.0, and depends on `kinotic-core` for the
proxies and on Vert.x for the transport. It auto-configures itself in a Spring Boot application whose application
class carries `@EnableKinotic`, as a Kinotic OS server's does. `kinotic-core` makes that application a Kinotic node
of its own, with its own Ignite instance and clustered Vert.x, even when it only calls a gateway; which nodes it
discovers, if any, is what its `kinotic.ignite` properties decide. The beans a service proxy is built from, the
converters of its arguments, replies and exceptions and the handlers of its return types, are the node's, so a
connection's proxies convert as a cluster member's do. The client adds a `KinoticConnectionFactory`, and every
connection runs on a context of its own on the node's Vert.x instance. A Kinotic OS server calls a gateway the same
way: its connections run on its own Vert.x instance and convert with its own mapper.

## Connecting

```java
import org.kinotic.client.api.ConnectOptions;
import org.kinotic.client.api.KinoticConnection;
import org.kinotic.client.api.KinoticConnectionFactory;

@Service
@RequiredArgsConstructor
public class LicenseKeys {

    private final KinoticConnectionFactory connectionFactory;

    public Future<KinoticConnection> connect(String secret) {
        ConnectOptions options = new ConnectOptions()
                .setServerUrl("wss://api.kinotic.ai")
                .setAuthHeaders(Map.of("clientId", "orders-sync", "clientSecret", secret, "organizationId", "acme-org"));
        return connectionFactory.connect(options);
    }

}
```

`serverUrl` names the gateway with a `ws://` or `wss://` URL; the client appends the gateway's STOMP endpoint
path itself. The `authHeaders` are sent on the WebSocket upgrade request, and the gateway's `SecurityService`
authenticates the connection from them before the STOMP session starts; the client never interprets them, so
whatever a gateway's security service expects, from a client id and secret to a bearer token, goes in the map as
headers. Rejected credentials fail the connect with an `AuthenticationException`, and any other 4xx answer to the
upgrade, such as the 404 of a URL that names no gateway, fails it with the rejection, since no retry changes
either. A gateway that cannot be reached, or that answers with a 5xx, is retried with the backoff of the options'
`ReconnectOptions`, forever unless `maxAttempts` bounds it. The connection keeps a copy of the options it was
opened with, so a change to them afterwards does not reach it; only what the TLS options cannot copy is shared with
it: the buffers holding its certificates, keys and revocation lists, and a key or trust manager factory it wraps.

Once connected, `connectedInfo()` holds what the gateway reported: the participant it authenticated the connection
as, and the reply destination id it issued for the connection. A `wss://` connection trusts the JDK's default
certificates; `setSslOptions` supplies its own trust or key material. Over `ws://` the `authHeaders` leave the
process in clear, so `connect` refuses them to a host other than localhost unless
`setAllowCleartextCredentials(true)` says the gateway is reached safely another way, behind a TLS-terminating proxy
or on a private network.

Two limits bound what the connection accepts, each defaulting to 2 MB, the default of the gateway's
`kinotic.maxEventPayloadSize`: `maxBodyLength`, the size a reply's body must stay under, and
`maxWebSocketFrameSize`, the largest WebSocket frame the client accepts. A reply over either ends the connection:
the call it answers fails with an `RpcServiceUnavailableException`, every other call in flight fails the same way,
and the client reconnects. A gateway whose `kinotic.maxEventPayloadSize` is raised writes replies in WebSocket
frames of up to that size, so both limits are raised with it; a request larger than that property is refused by
the gateway.

## Getting a proxy

A proxy is created for the published interface itself, or for a `@Proxy` interface mirroring the service's
methods. Each method of the interface returns a `CompletableFuture`, a Vert.x `Future`, a `Mono` or a `Flux`.

```java
@Publish
@Version("1.0.0")
@Zone("management-api")
public interface ITestService {

    CompletableFuture<String> testMethodWithString(String value);

    Flux<byte[]> getBinaryDataStream();

}

RpcServiceProxyHandle<ITestService> handle = connection.serviceProxy(ITestService.class);
ITestService testService = handle.getService();

String greeting = testService.testMethodWithString("Bob").get();        // "Hello Bob"
List<byte[]> chunks = testService.getBinaryDataStream().collectList().block();
```

`serviceProxy(ServiceIdentifier, Class)` addresses a service whose zone, namespace, name or version differ from the
interface's declarations. The proxies convert arguments and replies, and map a service's exception back to the
caller, exactly as a cluster member's proxies do: an exception the service threw arrives as that exception when
its class is on the client's classpath, otherwise as an `RpcInvocationException` naming it. Releasing a handle ends
its proxy; `close()` releases every proxy the connection still holds.

Arguments and replies are converted with the application's Jackson mapper, configured as a cluster member's: the
Vert.x types in their Vert.x wire format, the platform's own value types, and no constructor parameter detection.
A gateway's own mapper may carry more, such as the module a Kinotic OS server registers for its domain types, and
the modules the called services' types need are added the way any Spring Boot application customizes its mapper:

```java
@Bean
public JsonMapperBuilderCustomizer shapes() {
    return builder -> builder.addModule(new SimpleModule().addAbstractTypeMapping(Shape.class, Circle.class));
}
```

The proxies start a span for every invocation with the tracer of the application's `OpenTelemetry` bean, continued
by the service the invocation reaches; without an SDK on the classpath the bean is the global no-op one and the
proxies trace nothing.

The gateway authorizes every request against the zones the connection's participant may address (see
[CRI Format](https://kinotic.ai/platform/reference/cri-format)). A request to a zone outside them fails with an
`RpcInvocationException` whose message is the gateway's refusal. The gateway also closes the connection over the
refusal, as STOMP requires of a server that answers with an ERROR frame, so other calls in flight at that moment
fail as a lost connection does and the client reconnects.

## Connection lifecycle

The connection keeps itself alive with STOMP heartbeats, thirty seconds each way as the gateway offers, and reports
its `ConnectionState` to the handler set with `stateHandler`:

<table>
<thead>
  <tr>
    <th>
      State
    </th>
    
    <th>
      Meaning
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        CONNECTED
      </code>
    </td>
    
    <td>
      The gateway has accepted the connection; calls are sent as they are made.
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        RECONNECTING
      </code>
    </td>
    
    <td>
      The connection was lost and is being re-established with the backoff of the <code>
        ReconnectOptions
      </code>
      
      .
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        CLOSED
      </code>
    </td>
    
    <td>
      Closed for good, by <code>
        close()
      </code>
      
       or because the attempts the options allow were spent.
    </td>
  </tr>
</tbody>
</table>

A call pending when the connection is lost fails with an `RpcServiceUnavailableException`: the gateway releases
everything it held for the connection the moment it ends, so no reply can arrive on the next one (see
[Service Call Liveness](https://kinotic.ai/platform/service-call-liveness)). A call made while the connection is `RECONNECTING`
fails the same way at once rather than waiting for the reconnect. Every reconnect authenticates again with the
same headers and gets a reply destination of its own; the proxies already created keep working across it. A
reconnect retries and gives up on the same answers as the initial connect, with one exception: it retries rejected
credentials within the attempt budget, because a gateway whose own security service is briefly unavailable answers
every connection with the same 401.

The state handler runs on the connection's context, and so does the completion of a proxy result, since replies
arrive there: a handler that blocks on a call made through the connection deadlocks it.

```java
connection.stateHandler(state -> log.info("Kinotic connection is {}", state));

connection.close().await();
```

Publishing services from the Java client, as the TypeScript client can, is not supported in this version.


## Sitemap

See the full [sitemap](https://kinotic.ai/sitemap.md) for all pages.
