package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.management.api.config.LokiProperties;
import org.kinotic.management.api.services.LokiClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Vert.x-backed {@link LokiClient}: the tenant-scoped {@code WebClient} for {@code query_range} and a
 * {@link WebSocketClient} for the {@code tail} stream.
 */
@Slf4j
@Component
public class DefaultLokiClient extends AbstractTenantScopedClient implements LokiClient {

    private static final String QUERY_RANGE_PATH = "/loki/api/v1/query_range";
    private static final String TAIL_PATH = "/loki/api/v1/tail";

    private final LokiProperties lokiProperties;
    private WebSocketClient webSocketClient;

    public DefaultLokiClient(Vertx vertx, LokiProperties lokiProperties) {
        super(vertx);
        this.lokiProperties = lokiProperties;
    }

    @PostConstruct
    public void start() {
        this.webSocketClient = vertx.createWebSocketClient();
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
        return get(lokiProperties.getUrl() + QUERY_RANGE_PATH,
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

    private WebSocketConnectOptions tailOptions(String tenant, String query) {
        URI base = URI.create(lokiProperties.getUrl());
        boolean ssl = "https".equalsIgnoreCase(base.getScheme());
        int port = base.getPort() != -1 ? base.getPort() : (ssl ? 443 : 80);
        return new WebSocketConnectOptions()
                .setHost(base.getHost())
                .setPort(port)
                .setSsl(ssl)
                .setURI(TAIL_PATH + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8))
                .addHeader(ORG_ID_HEADER, tenant);
    }

    private static long msToNs(long epochMs) {
        return epochMs * 1_000_000L;
    }
}
