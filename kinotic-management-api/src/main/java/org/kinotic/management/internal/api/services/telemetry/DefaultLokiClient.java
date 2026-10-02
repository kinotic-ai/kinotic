package org.kinotic.management.internal.api.services.telemetry;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.management.api.config.ManagementApiProperties;
import org.kinotic.management.api.services.telemetry.LokiClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Vert.x-backed {@link LokiClient}: the tenant-scoped {@code WebClient} for {@code query_range} and
 * {@code delete}, and a {@link WebSocketClient} for the {@code tail} stream.
 */
@Slf4j
@Component
public class DefaultLokiClient extends AbstractTenantScopedClient implements LokiClient {

    private static final String QUERY_RANGE_PATH = "/loki/api/v1/query_range";
    private static final String TAIL_PATH = "/loki/api/v1/tail";
    private static final String DELETE_PATH = "/loki/api/v1/delete";
    // Loki sends a tail response of up to 100 entries, each with its stream's labels, as a single WebSocket
    // frame; the Vert.x default of 64 KB fails a tail on a batch of long lines, so this allows 10 MB
    private static final int TAIL_MAX_MESSAGE_BYTES = 10 * 1024 * 1024;

    private final String lokiUrl;
    private WebSocketClient webSocketClient;

    public DefaultLokiClient(Vertx vertx, ManagementApiProperties properties) {
        super(vertx);
        this.lokiUrl = properties.getLokiUrl();
    }

    @PostConstruct
    public void start() {
        this.webSocketClient = vertx.createWebSocketClient(new WebSocketClientOptions()
                                                                   .setMaxFrameSize(TAIL_MAX_MESSAGE_BYTES)
                                                                   .setMaxMessageSize(TAIL_MAX_MESSAGE_BYTES));
    }

    @Override
    @PreDestroy
    public void stop() {
        super.stop();
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }

    @Override
    public Future<Buffer> queryRange(String tenant, String query, long start, long end, int limit) {
        return get(lokiUrl + QUERY_RANGE_PATH,
                   Map.of("query", query,
                          "start", Long.toString(msToNs(start)),
                          "end", Long.toString(msToNs(end)),
                          "limit", Integer.toString(limit)),
                   tenant,
                   "Loki query_range");
    }

    @Override
    public Flux<Buffer> tail(String tenant, String query) {
        return Flux.create(sink -> webSocketClient.connect(tailOptions(tenant, query))
                .onSuccess(ws -> {
                    // The Flux can be cancelled before the socket finishes opening; close it straight away.
                    if (sink.isCancelled()) {
                        ws.close();
                        return;
                    }
                    ws.textMessageHandler(message -> sink.next(Buffer.buffer(message)));
                    ws.exceptionHandler(sink::error);
                    ws.closeHandler(unused -> sink.complete());
                    sink.onCancel(ws::close);
                })
                .onFailure(sink::error));
    }

    @Override
    public Future<Void> delete(String tenant, String query, long start, long end) {
        // the delete API reads RFC3339, and takes unix seconds only as exactly ten digits, which
        // rules out an epoch start
        return post(lokiUrl + DELETE_PATH,
                    Map.of("query", query,
                           "start", Instant.ofEpochMilli(start).toString(),
                           "end", Instant.ofEpochMilli(end).toString()),
                    tenant,
                    "Loki delete");
    }

    private WebSocketConnectOptions tailOptions(String tenant, String query) {
        URI base = URI.create(lokiUrl);
        boolean ssl = "https".equalsIgnoreCase(base.getScheme());
        int port = base.getPort() != -1 ? base.getPort() : (ssl ? 443 : 80);
        return new WebSocketConnectOptions()
                .setHost(base.getHost())
                .setPort(port)
                .setSsl(ssl)
                // Loki opens a tail by replaying up to 100 entries of the hour before start; starting at now
                // leaves that history to queryRange, which the caller has already read
                .setURI(TAIL_PATH + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&start=" + msToNs(System.currentTimeMillis()))
                .addHeader(ORG_ID_HEADER, tenant);
    }

    private static long msToNs(long epochMs) {
        return epochMs * 1_000_000L;
    }
}
