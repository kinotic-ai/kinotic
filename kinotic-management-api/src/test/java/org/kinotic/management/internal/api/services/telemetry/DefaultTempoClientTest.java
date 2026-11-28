package org.kinotic.management.internal.api.services.telemetry;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.management.api.config.ManagementApiProperties;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the requests {@link DefaultTempoClient} sends a Tempo HTTP API, served here by a local
 * server that records each request and answers it with an empty trace.
 */
class DefaultTempoClientTest {

    private static final AtomicReference<MultiMap> params = new AtomicReference<>();
    private static final AtomicReference<String> tenant = new AtomicReference<>();

    private static Vertx vertx;
    private static DefaultTempoClient client;

    @BeforeAll
    static void startTempo() throws Exception {
        vertx = Vertx.vertx();
        HttpServer server = vertx.createHttpServer()
                                 .requestHandler(request -> {
                                     params.set(request.params());
                                     tenant.set(request.getHeader("X-Scope-OrgID"));
                                     request.response().end("{}");
                                 })
                                 .listen(0)
                                 .await(10, TimeUnit.SECONDS);
        client = new DefaultTempoClient(vertx, new ManagementApiProperties().setTempoUrl("http://localhost:" + server.actualPort()));
    }

    @AfterAll
    static void stopTempo() {
        vertx.close();
    }

    @Test
    void traceLookupSendsItsRangeInWholeSecondsCoveringTheMilliseconds() throws Exception {
        client.findTrace("acme", "abc123", 1_500, 2_200).await(10, TimeUnit.SECONDS);

        assertEquals("acme", tenant.get());
        assertEquals("1", params.get().get("start"));
        assertEquals("3", params.get().get("end"));
    }

    @Test
    void traceLookupEndsAfterItsStartWhenTheRangeIsOneInstant() throws Exception {
        client.findTrace("acme", "abc123", 2_000, 2_000).await(10, TimeUnit.SECONDS);

        assertEquals("2", params.get().get("start"));
        assertEquals("3", params.get().get("end"));
    }
}
