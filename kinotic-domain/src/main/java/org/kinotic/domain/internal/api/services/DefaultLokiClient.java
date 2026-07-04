package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.config.LokiProperties;
import org.kinotic.domain.api.services.LokiClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Vert.x-backed {@link LokiClient}: a {@link WebClient} for {@code query_range} and a {@link WebSocketClient}
 * for the {@code tail} stream.
 */
@Slf4j
@Component
public class DefaultLokiClient implements LokiClient {

    private static final String ORG_ID_HEADER = "X-Scope-OrgID";
    private static final String QUERY_RANGE_PATH = "/loki/api/v1/query_range";
    private static final String TAIL_PATH = "/loki/api/v1/tail";

    private final Vertx vertx;
    private final LokiProperties lokiProperties;
    private WebClient webClient;
    private WebSocketClient webSocketClient;

    public DefaultLokiClient(Vertx vertx, KinoticDomainProperties properties) {
        this.vertx = vertx;
        this.lokiProperties = properties.getDomain().getLoki();
    }

    @PostConstruct
    public void start() {
        this.webClient = WebClient.create(vertx);
        this.webSocketClient = vertx.createWebSocketClient();
    }

    @PreDestroy
    public void stop() {
        if (webClient != null) {
            webClient.close();
        }
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }

    @Override
    public Future<Buffer> queryRange(String tenant, String query, long start, long end, int limit) {
        return webClient.getAbs(lokiProperties.getUrl() + QUERY_RANGE_PATH)
                        .addQueryParam("query", query)
                        .addQueryParam("start", Long.toString(msToNs(start)))
                        .addQueryParam("end", Long.toString(msToNs(end)))
                        .addQueryParam("limit", Integer.toString(limit))
                        .putHeader(ORG_ID_HEADER, tenant)
                        .send()
                        .compose(resp -> resp.statusCode() == 200
                                ? Future.succeededFuture(resp.body())
                                : Future.failedFuture("Loki query_range failed: HTTP " + resp.statusCode()
                                        + (resp.bodyAsString() != null ? " — " + resp.bodyAsString() : "")));
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
