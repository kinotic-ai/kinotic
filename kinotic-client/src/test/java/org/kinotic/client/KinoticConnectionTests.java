package org.kinotic.client;

import io.vertx.core.Vertx;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kinotic.client.api.ConnectOptions;
import org.kinotic.client.api.ConnectionState;
import org.kinotic.client.api.KinoticConnection;
import org.kinotic.client.api.ReconnectOptions;
import org.kinotic.client.gateway.Circle;
import org.kinotic.client.gateway.ClientTestService;
import org.kinotic.client.gateway.DefaultClientTestService;
import org.kinotic.client.gateway.RestrictedService;
import org.kinotic.client.gateway.Shape;
import org.kinotic.client.gateway.TestDto;
import org.kinotic.client.gateway.TestGatewayApplication;
import org.kinotic.client.gateway.TestGatewaySecurityService;
import org.kinotic.core.api.RpcServiceProxyHandle;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.exceptions.RpcInvocationException;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.service.RequestLivenessWatcher;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.module.SimpleModule;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Runs the Java client against the gateway, started in this JVM, over a real WebSocket: every supported return type,
 * a stream and its cancellation, a service's exception, a refusal by the gateway, rejected credentials, a call
 * pending when the gateway stops, and the reconnect once it is back.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
public class KinoticConnectionTests {

    private static final long TIMEOUT_SECONDS = 15;

    private static int port;
    private static ConfigurableApplicationContext gateway;
    private static Vertx vertx;
    private final List<KinoticConnection> connections = new ArrayList<>();

    @BeforeAll
    public static void startGateway() throws Exception {
        port = TestGatewayApplication.freePort();
        gateway = TestGatewayApplication.start(port);
        // one instance for every connection of the suite, as an application sharing its own would have
        vertx = Vertx.vertx();
    }

    @AfterAll
    public static void stopGateway() throws Exception {
        try {
            if (gateway != null) {
                gateway.close();
            }
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @AfterEach
    public void closeConnections() throws Exception {
        for (KinoticConnection connection : connections) {
            connection.close().toCompletionStage().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        connections.clear();
    }

    @Test
    public void testEverySupportedReturnType() throws Exception {
        KinoticConnection connection = connect(options(TestGatewayApplication.CLIENT_SECRET));
        ConnectedInfo connectedInfo = connection.connectedInfo();
        Assertions.assertEquals("java-client", connectedInfo.getParticipant().getId());
        Assertions.assertEquals(List.of("ADMIN"), connectedInfo.getParticipant().getRoles());
        Assertions.assertNotNull(connectedInfo.getReplyToId());
        Assertions.assertEquals(ConnectionState.CONNECTED, connection.state());

        ClientTestService service = connection.serviceProxy(ClientTestService.class).getService();

        Assertions.assertEquals("Hello Bob", service.greet("Bob").get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Assertions.assertEquals(5, service.add(2, 3).toCompletionStage().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        TestDto described = service.describe(new TestDto().setName("widget").setCount(3)).block(Duration.ofSeconds(TIMEOUT_SECONDS));
        Assertions.assertEquals(new TestDto().setName("widget").setCount(3).setDescription("3 x widget"), described);
        Assertions.assertEquals(List.of(1, 2, 3, 4, 5), service.count(5).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS)));
    }

    @Test
    public void testMapperCustomizerConvertsWhatTheDefaultMapperCannot() throws Exception {
        // the gateway serializes the Circle; a Shape return type converts only once a module names the Circle
        KinoticConnection plain = connect(options(TestGatewayApplication.CLIENT_SECRET));
        ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                                                             () -> plain.serviceProxy(ClientTestService.class).getService().shape(2).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Assertions.assertNotNull(failure.getCause());

        KinoticConnection customized = connect(options(TestGatewayApplication.CLIENT_SECRET)
                                                       .setJsonMapperCustomizer(builder -> builder.addModule(new SimpleModule().addAbstractTypeMapping(Shape.class, Circle.class))));

        Shape shape = customized.serviceProxy(ClientTestService.class).getService().shape(2).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Assertions.assertEquals(new Circle().setRadius(2), shape);
    }

    @Test
    public void testStreamCancellationStopsTheService() throws Exception {
        KinoticConnection connection = connect(options(TestGatewayApplication.CLIENT_SECRET));
        ClientTestService service = connection.serviceProxy(ClientTestService.class).getService();
        DefaultClientTestService serving = gateway.getBean(DefaultClientTestService.class);

        List<Long> ticks = service.ticks().take(3).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS));

        Assertions.assertEquals(List.of(0L, 1L, 2L), ticks);
        // the cancel reaches the service through the gateway and ends the stream it was producing
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> serving.activeTicks() == 0);
    }

    @Test
    public void testServiceExceptionArrivesAsTheExceptionTheServiceThrew() throws Exception {
        KinoticConnection connection = connect(options(TestGatewayApplication.CLIENT_SECRET));
        ClientTestService service = connection.serviceProxy(ClientTestService.class).getService();

        ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                                                             () -> service.failWith("boom").get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        Assertions.assertInstanceOf(IllegalStateException.class, failure.getCause());
        Assertions.assertEquals("boom", failure.getCause().getMessage());
    }

    @Test
    public void testRefusedDestinationFailsTheCallAndTheConnectionRecovers() throws Exception {
        KinoticConnection connection = connect(options(TestGatewayApplication.CLIENT_SECRET));
        RestrictedService restricted = connection.serviceProxy(RestrictedService.class).getService();
        ClientTestService service = connection.serviceProxy(ClientTestService.class).getService();

        ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                                                             () -> restricted.secret().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        Assertions.assertInstanceOf(RpcInvocationException.class, failure.getCause());
        Assertions.assertTrue(failure.getCause().getMessage().contains("Not Authorized"), failure.getCause().getMessage());
        // the gateway ends the connection over the refusal; the client reconnects and the next call goes through
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                  .ignoreExceptions()
                  .until(() -> "Hello again".equals(service.greet("again").get(5, TimeUnit.SECONDS)));
        Assertions.assertEquals(ConnectionState.CONNECTED, connection.state());
    }

    @Test
    public void testRejectedCredentialsFailTheConnect() {
        ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                                                             () -> KinoticConnection.connect(vertx, options("wrong-secret"))
                                                                                    .toCompletionStage()
                                                                                    .toCompletableFuture()
                                                                                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        Assertions.assertInstanceOf(AuthenticationException.class, failure.getCause());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public void testPendingCallFailsWhenTheGatewayStopsAndTheConnectionRecoversWhenItRestarts() throws Exception {
        KinoticConnection connection = connect(options(TestGatewayApplication.CLIENT_SECRET));
        List<ConnectionState> states = Collections.synchronizedList(new ArrayList<>());
        connection.stateHandler(states::add);
        RpcServiceProxyHandle<ClientTestService> handle = connection.serviceProxy(ClientTestService.class);
        ClientTestService service = handle.getService();
        Assertions.assertEquals("Hello Bob", service.greet("Bob").get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        // a second connection whose attempt budget the outage exhausts
        KinoticConnection bounded = connect(options(TestGatewayApplication.CLIENT_SECRET).setReconnect(reconnectOptions().setMaxAttempts(2)));

        CompletableFuture<String> pending = service.never();
        // the gateway watches the request once a node acknowledged it, which is what the outage must cut short
        RequestLivenessWatcher gatewayWatcher = gateway.getBean(RequestLivenessWatcher.class);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> gatewayWatcher.watchedCount() == 1);
        gateway.close();
        gateway = null;
        long stopped = System.nanoTime();
        try {
            ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                                                                 () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(RpcServiceUnavailableException.class, failure.getCause());
            Assertions.assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - stopped) < TIMEOUT_SECONDS);
            Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> states.contains(ConnectionState.RECONNECTING));

            // a call made while the gateway is down fails at once
            ExecutionException down = Assertions.assertThrows(ExecutionException.class,
                                                              () -> service.greet("down").get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(RpcServiceUnavailableException.class, down.getCause());

            // the bounded connection ends for good; it still closes, and mints no further proxies
            Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> bounded.state() == ConnectionState.CLOSED);
            bounded.close().toCompletionStage().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Assertions.assertThrows(IllegalStateException.class, () -> bounded.serviceProxy(ClientTestService.class));

            // the gateway comes back rejecting every connection, as one whose security service is failing does; the
            // reconnect keeps trying until it is admitted
            TestGatewaySecurityService.rejectEveryone = true;
            TestGatewaySecurityService.rejections.set(0);
            try {
                gateway = TestGatewayApplication.start(port);
                Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> TestGatewaySecurityService.rejections.get() >= 2);
                Assertions.assertEquals(ConnectionState.RECONNECTING, connection.state());
            } finally {
                TestGatewaySecurityService.rejectEveryone = false;
            }
        } finally {
            // the other tests need the gateway back whatever this one found
            if (gateway == null) {
                gateway = TestGatewayApplication.start(port);
            }
        }

        Awaitility.await().atMost(1, TimeUnit.MINUTES)
                  .ignoreExceptions()
                  .until(() -> "Hello back".equals(service.greet("back").get(5, TimeUnit.SECONDS)));
        Assertions.assertEquals(ConnectionState.CONNECTED, connection.state());
        // the reconnect issued a reply destination of its own
        Assertions.assertNotNull(connection.connectedInfo().getReplyToId());
        handle.release();
    }

    private KinoticConnection connect(ConnectOptions options) throws Exception {
        KinoticConnection ret = KinoticConnection.connect(vertx, options)
                                                 .toCompletionStage()
                                                 .toCompletableFuture()
                                                 .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        connections.add(ret);
        return ret;
    }

    private static ConnectOptions options(String clientSecret) {
        return new ConnectOptions().setServerUrl("ws://localhost:" + port)
                                   .setAuthHeaders(Map.of("clientId", "java-client", "clientSecret", clientSecret))
                                   .setReconnect(reconnectOptions());
    }

    private static ReconnectOptions reconnectOptions() {
        return new ReconnectOptions().setInitialDelay(Duration.ofMillis(200))
                                     .setMaxDelay(Duration.ofSeconds(1))
                                     .setMaxJitter(Duration.ZERO);
    }

}
