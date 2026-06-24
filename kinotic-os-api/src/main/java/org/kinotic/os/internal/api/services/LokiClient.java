package org.kinotic.os.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.config.LokiProperties;
import org.kinotic.os.api.model.LogQuery;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Transport to the Loki HTTP API: historical {@code query_range} requests and live {@code tail} over a
 * WebSocket. Responses are returned as raw bytes for passthrough to the caller. Each request carries the
 * given organization id as the Loki {@code X-Scope-OrgID} tenant header.
 */
@Slf4j
@Component
public class LokiClient {

    private static final String ORG_ID_HEADER = "X-Scope-OrgID";
    private static final String QUERY_RANGE_PATH = "/loki/api/v1/query_range";
    private static final String TAIL_PATH = "/loki/api/v1/tail";

    private final Vertx vertx;
    private final LokiProperties lokiProperties;
    private WebClient webClient;
    private WebSocketClient webSocketClient;

    public LokiClient(Vertx vertx, LokiProperties lokiProperties) {
        this.vertx = vertx;
        this.lokiProperties = lokiProperties;
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

    /**
     * Runs a Loki {@code query_range} and returns the raw response body.
     *
     * @param orgId the Loki tenant ({@code X-Scope-OrgID})
     * @param query the LogQL selector, time range, and limit
     * @return a {@link Future} of the raw Loki response body
     */
    public Future<Buffer> queryRange(String orgId, LogQuery query) {
        return webClient.getAbs(lokiProperties.getUrl() + QUERY_RANGE_PATH)
                        .addQueryParam("query", query.getQuery())
                        .addQueryParam("start", Long.toString(msToNs(query.getStart())))
                        .addQueryParam("end", Long.toString(msToNs(query.getEnd())))
                        .addQueryParam("limit", Integer.toString(query.getLimit()))
                        .putHeader(ORG_ID_HEADER, orgId)
                        .send()
                        .compose(resp -> resp.statusCode() == 200
                                ? Future.succeededFuture(resp.body())
                                : Future.failedFuture("Loki query_range failed: HTTP " + resp.statusCode()
                                        + (resp.bodyAsString() != null ? " — " + resp.bodyAsString() : "")));
    }

    /**
     * Opens a Loki {@code tail} WebSocket and emits each tail frame's raw bytes. The WebSocket is closed when
     * the returned {@link Flux} is cancelled or completed.
     *
     * @param orgId the Loki tenant ({@code X-Scope-OrgID})
     * @param query the LogQL query to follow
     * @return a {@link Flux} of raw Loki tail frame bytes
     */
    public Flux<byte[]> tail(String orgId, String query) {
        return Flux.create(sink -> webSocketClient.connect(tailOptions(orgId, query))
                .onSuccess(ws -> {
                    // The Flux can be cancelled before the socket finishes opening; close it straight away.
                    if (sink.isCancelled()) {
                        ws.close();
                        return;
                    }
                    ws.textMessageHandler(message -> sink.next(message.getBytes(StandardCharsets.UTF_8)));
                    ws.exceptionHandler(sink::error);
                    ws.closeHandler(unused -> sink.complete());
                    sink.onCancel(ws::close);
                })
                .onFailure(sink::error));
    }

    private WebSocketConnectOptions tailOptions(String orgId, String query) {
        URI base = URI.create(lokiProperties.getUrl());
        boolean ssl = "https".equalsIgnoreCase(base.getScheme());
        int port = base.getPort() != -1 ? base.getPort() : (ssl ? 443 : 80);
        return new WebSocketConnectOptions()
                .setHost(base.getHost())
                .setPort(port)
                .setSsl(ssl)
                .setURI(TAIL_PATH + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8))
                .addHeader(ORG_ID_HEADER, orgId);
    }

    private static long msToNs(long epochMs) {
        return epochMs * 1_000_000L;
    }
}
