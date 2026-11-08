package org.kinotic.client;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.UpgradeRejectedException;
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
import org.kinotic.client.api.KinoticConnectionFactory;
import org.kinotic.client.api.ReconnectOptions;
import org.kinotic.client.app.TestClientApplication;
import org.kinotic.client.gateway.Circle;
import org.kinotic.client.gateway.ClientTestService;
import org.kinotic.client.gateway.DefaultClientTestService;
import org.kinotic.client.gateway.RestrictedService;
import org.kinotic.client.gateway.Shape;
import org.kinotic.client.gateway.TestDto;
import org.kinotic.client.gateway.TestGatewayApplication;
import org.kinotic.client.gateway.TestGatewaySecurityService;
import org.kinotic.core.api.RpcServiceProxyHandle;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.exceptions.RpcInvocationException;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.service.RequestLivenessWatcher;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Runs the Java client, auto-configured in a Spring context of its own, against the gateway started in this JVM
 * over a real WebSocket: every supported return type,
 * a stream and its cancellation, a service's exception, a refusal by the gateway, rejected credentials, a call
 * pending when the gateway stops, and the reconnect once it is back.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
public class KinoticConnectionTests {

    private static final long TIMEOUT_SECONDS = 15;

    private static int port;
    private static ConfigurableApplicationContext gateway;
    private static ConfigurableApplicationContext client;
    private static KinoticConnectionFactory factory;
    private static Vertx vertx;
    private final List<KinoticConnection> connections = new ArrayList<>();

    @BeforeAll
    public static void startGateway() throws Exception {
        client = TestClientApplication.start();
        factory = client.getBean(KinoticConnectionFactory.class);
        // the application's instance, shared by every connection of the suite
        vertx = client.getBean(Vertx.class);
        port = TestGatewayApplication.freePort();
        gateway = TestGatewayApplication.start(port);
    }

    @AfterAll
    public static void stopGateway() {
        try {
            if (gateway != null) {
                gateway.close();
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    @AfterEach
    public void closeConnections() throws Exception {
        try {
            List<Future<Void>> closes = connections.stream().map(KinoticConnection::close).toList();
            await(Future.all(closes));
        } finally {
            connections.clear();
        }
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
        Assertions.assertEquals(5, await(service.add(2, 3)));
        TestDto described = service.describe(new TestDto().setName("widget").setCount(3)).block(Duration.ofSeconds(TIMEOUT_SECONDS));
        Assertions.assertEquals(new TestDto().setName("widget").setCount(3).setDescription("3 x widget"), described);
        Assertions.assertEquals(List.of(1, 2, 3, 4, 5), service.count(5).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS)));
    }

    @Test
    public void testReplyLargerThanADefaultWebSocketFrameArrives() throws Exception {
        // the gateway writes a reply in WebSocket frames of up to its payload limit, well over Vert.x's 64 KB default
        KinoticConnection connection = connect(options(TestGatewayApplication.CLIENT_SECRET));
        ClientTestService service = connection.serviceProxy(ClientTestService.class).getService();

        String reply = service.pad(200_000).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Assertions.assertEquals(200_000, reply.length());
    }

    @Test
    public void testReplyOverTheBodyLimitFailsTheCallAndTheConnectionRecovers() throws Exception {
        KinoticConnection connection = connect(options(TestGatewayApplication.CLIENT_SECRET).setMaxBodyLength(1024));
        assertCallLostToTheReplyAndTheConnectionRecovers(connection);
    }

    @Test
    public void testReplyOverTheWebSocketFrameLimitFailsTheCallAndTheConnectionRecovers() throws Exception {
        // the gateway writes the reply in one WebSocket frame, over a limit that still holds every other frame of
        // the connection, so it is the transport that refuses the reply
        KinoticConnection connection = connect(options(TestGatewayApplication.CLIENT_SECRET).setMaxWebSocketFrameSize(65536));
        assertCallLostToTheReplyAndTheConnectionRecovers(connection);
    }

    private void assertCallLostToTheReplyAndTheConnectionRecovers(KinoticConnection connection) throws Exception {
        List<ConnectionState> states = Collections.synchronizedList(new ArrayList<>());
        connection.stateHandler(states::add);
        ClientTestService service = connection.serviceProxy(ClientTestService.class).getService();

        ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                                                             () -> service.pad(200_000).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        Assertions.assertInstanceOf(RpcServiceUnavailableException.class, failure.getCause());
        // the reply ended the connection, which the client re-establishes
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                  .until(() -> states.indexOf(ConnectionState.RECONNECTING) >= 0
                          && states.lastIndexOf(ConnectionState.CONNECTED) > states.indexOf(ConnectionState.RECONNECTING));
        Assertions.assertEquals("Hello Bob", service.greet("Bob").get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void testProxyReleasedBeforeItsSubscriptionIsReceiptedKeepsTheConnection() throws Exception {
        // a subscription the gateway never receipts loses the connection after the connection timeout, which the
        // observation below outlasts
        KinoticConnection connection = connect(options(TestGatewayApplication.CLIENT_SECRET)
                                                       .setReconnect(reconnectOptions().setConnectionTimeout(Duration.ofMillis(500))));
        List<ConnectionState> states = Collections.synchronizedList(new ArrayList<>());
        connection.stateHandler(states::add);
        ClientTestService service = connection.serviceProxy(ClientTestService.class).getService();
        String replyToId = connection.connectedInfo().getReplyToId();

        // each UNSUBSCRIBE reaches the gateway while the SUBSCRIBE before it is still registering in the cluster
        for (int i = 0; i < 20; i++) {
            connection.serviceProxy(ClientTestService.class).release();
        }

        Assertions.assertEquals("Hello Bob", service.greet("Bob").get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        // the gateway ends a connection asynchronously, so the connection has to stay up for a while to pass
        Awaitility.await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2))
                  .until(() -> connection.state() == ConnectionState.CONNECTED && !states.contains(ConnectionState.RECONNECTING));
        Assertions.assertEquals(replyToId, connection.connectedInfo().getReplyToId());
    }

    @Test
    public void testTheApplicationsJacksonCustomizationConvertsTheReply() throws Exception {
        // the gateway serializes the Circle; a Shape return type converts through the module the application's
        // customizer adds to its mapper, which the proxies convert with
        KinoticConnection connection = connect(options(TestGatewayApplication.CLIENT_SECRET));

        Shape shape = connection.serviceProxy(ClientTestService.class).getService().shape(2).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

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
    public void testServerUrlEndingInTheGatewayPathIsRejected() {
        for (String serverUrl : List.of("ws://localhost:" + port + EventConstants.STOMP_WEBSOCKET_PATH,
                                        "ws://localhost:" + port + EventConstants.STOMP_WEBSOCKET_PATH + "/")) {
            Assertions.assertThrows(IllegalArgumentException.class,
                                    () -> factory.connect(options(TestGatewayApplication.CLIENT_SECRET).setServerUrl(serverUrl)),
                                    serverUrl);
        }
    }

    @Test
    public void testAuthHeaderWithAnInvalidNameIsRejected() {
        ConnectOptions options = options(TestGatewayApplication.CLIENT_SECRET).setAuthHeaders(Map.of("client id", "java-client"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> factory.connect(options));
    }

    @Test
    public void testOptionsOutsideTheirConstraintsAreRejected() {
        IllegalArgumentException tooSmall = Assertions.assertThrows(IllegalArgumentException.class,
                                                                    () -> factory.connect(options(TestGatewayApplication.CLIENT_SECRET).setMaxBodyLength(0)));
        Assertions.assertTrue(tooSmall.getMessage().contains("maxBodyLength"), tooSmall.getMessage());

        // the reconnect options validate with the options they belong to
        IllegalArgumentException nested = Assertions.assertThrows(IllegalArgumentException.class,
                                                                  () -> factory.connect(options(TestGatewayApplication.CLIENT_SECRET)
                                                                                           .setReconnect(reconnectOptions().setMaxJitter(null))));
        Assertions.assertTrue(nested.getMessage().contains("reconnect.maxJitter"), nested.getMessage());
    }

    @Test
    public void testCredentialsOverCleartextToARemoteHostNeedTheOption() {
        // 192.0.2.1 is reserved for documentation, so it is not loopback and nothing answers there
        ConnectOptions options = options(TestGatewayApplication.CLIENT_SECRET)
                .setServerUrl("ws://192.0.2.1:" + port)
                .setReconnect(reconnectOptions().setMaxAttempts(1).setConnectionTimeout(Duration.ofMillis(200)));
        Assertions.assertThrows(IllegalArgumentException.class, () -> factory.connect(options));
        // a hostname is not a loopback literal, however much it looks like one
        Assertions.assertThrows(IllegalArgumentException.class,
                                () -> factory.connect(options(TestGatewayApplication.CLIENT_SECRET).setServerUrl("ws://127.example.com:" + port)));

        // allowed, the connect goes on to its attempt, which the unanswered host fails
        ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                                                             () -> factory.connect(options.setAllowCleartextCredentials(true))
                                                                                    .toCompletionStage()
                                                                                    .toCompletableFuture()
                                                                                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Assertions.assertFalse(failure.getCause() instanceof IllegalArgumentException, failure.getCause().toString());
    }

    @Test
    public void testInitialConnectEndsAtOnceOnARejectedUpgrade() throws Exception {
        // a server answering the upgrade with a 404, as one with no gateway at the path does; a retried 404 would
        // never complete the connect, since the attempt budget is unbounded
        HttpServer server = await(vertx.createHttpServer()
                                       .requestHandler(request -> request.response().setStatusCode(404).end())
                                       .listen(0));
        try {
            ConnectOptions options = options(TestGatewayApplication.CLIENT_SECRET).setServerUrl("ws://localhost:" + server.actualPort());

            ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                                                                 () -> factory.connect(options)
                                                                                        .toCompletionStage()
                                                                                        .toCompletableFuture()
                                                                                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            Assertions.assertInstanceOf(UpgradeRejectedException.class, failure.getCause());
            Assertions.assertEquals(404, ((UpgradeRejectedException) failure.getCause()).getStatus());
        } finally {
            await(server.close());
        }
    }

    @Test
    public void testRejectedCredentialsFailTheConnect() {
        ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                                                             () -> factory.connect(options("wrong-secret"))
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
            await(bounded.close());
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

    @Test
    public void testInitialConnectEndsAfterTheAttemptsAllowed() throws Exception {
        // nothing listens on the port, so every attempt is refused
        ConnectOptions options = options(TestGatewayApplication.CLIENT_SECRET)
                .setServerUrl("ws://localhost:" + TestGatewayApplication.freePort())
                .setReconnect(reconnectOptions().setMaxAttempts(2));
        long started = System.nanoTime();

        ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                                                             () -> factory.connect(options)
                                                                                    .toCompletionStage()
                                                                                    .toCompletableFuture()
                                                                                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        Assertions.assertFalse(failure.getCause() instanceof AuthenticationException, failure.getCause().toString());
        // the second attempt waited for the initial delay
        Assertions.assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= 200);
    }

    @Test
    public void testInitialConnectTimesOutOnAGatewayThatNeverAnswers() throws Exception {
        // a server that accepts the socket and never answers the upgrade
        try (ServerSocket silent = new ServerSocket(0)) {
            ConnectOptions options = options(TestGatewayApplication.CLIENT_SECRET)
                    .setServerUrl("ws://localhost:" + silent.getLocalPort())
                    .setReconnect(reconnectOptions().setMaxAttempts(1).setConnectionTimeout(Duration.ofMillis(500)));
            long started = System.nanoTime();

            ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                                                                 () -> factory.connect(options)
                                                                                        .toCompletionStage()
                                                                                        .toCompletableFuture()
                                                                                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            // the attempt's own timer or the WebSocket client's timeout ends the attempt, whichever fires first
            Assertions.assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) < 5, failure.getCause().toString());
        }
    }

    private KinoticConnection connect(ConnectOptions options) throws Exception {
        KinoticConnection ret = await(factory.connect(options));
        connections.add(ret);
        return ret;
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
