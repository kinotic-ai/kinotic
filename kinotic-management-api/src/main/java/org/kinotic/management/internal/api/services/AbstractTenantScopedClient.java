package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PreDestroy;

import java.util.Map;

/**
 * Base of the clients of the multi-tenant Grafana backends (Loki, Tempo, Mimir): a Vert.x
 * {@link WebClient} whose requests carry the tenant as {@code X-Scope-OrgID}. A GET answers with the
 * raw body of a 200 response and a POST completes on a 2xx one, either failing with the backend's own
 * message on anything else.
 */
public abstract class AbstractTenantScopedClient {

    protected static final String ORG_ID_HEADER = "X-Scope-OrgID";

    protected final Vertx vertx;
    private final WebClient webClient;

    protected AbstractTenantScopedClient(Vertx vertx) {
        this.vertx = vertx;
        this.webClient = WebClient.create(vertx);
    }

    @PreDestroy
    public void stop() {
        webClient.close();
    }

    /**
     * Runs a GET against the backend as the given tenant.
     *
     * @param url       the absolute URL of the endpoint
     * @param params    query parameters, sent as given
     * @param tenant    the tenant ({@code X-Scope-OrgID})
     * @param operation what the request is, named in the failure message
     * @return a {@link Future} of the raw response body
     */
    protected Future<Buffer> get(String url, Map<String, String> params, String tenant, String operation) {
        HttpRequest<Buffer> request = webClient.getAbs(url).putHeader(ORG_ID_HEADER, tenant);
        params.forEach(request::addQueryParam);
        return request.send()
                      .compose(resp -> resp.statusCode() == 200
                              ? Future.succeededFuture(resp.body())
                              : Future.failedFuture(operation + " failed: HTTP " + resp.statusCode()
                                      + (resp.bodyAsString() != null ? " — " + resp.bodyAsString() : "")));
    }

    /**
     * Runs a POST with no body against the backend as the given tenant.
     *
     * @param url       the absolute URL of the endpoint
     * @param params    query parameters, sent as given
     * @param tenant    the tenant ({@code X-Scope-OrgID})
     * @param operation what the request is, named in the failure message
     * @return a {@link Future} that completes when the backend accepted the request
     */
    protected Future<Void> post(String url, Map<String, String> params, String tenant, String operation) {
        HttpRequest<Buffer> request = webClient.postAbs(url).putHeader(ORG_ID_HEADER, tenant);
        params.forEach(request::addQueryParam);
        return request.send()
                      .compose(resp -> resp.statusCode() >= 200 && resp.statusCode() < 300
                              ? Future.<Void>succeededFuture()
                              : Future.failedFuture(operation + " failed: HTTP " + resp.statusCode()
                                      + (resp.bodyAsString() != null ? " — " + resp.bodyAsString() : "")));
    }

    // Tempo, Prometheus and the Loki delete API take their time ranges in epoch seconds
    protected static long msToSeconds(long epochMs) {
        return epochMs / 1000;
    }
}
