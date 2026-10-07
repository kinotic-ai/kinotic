package org.kinotic.domain.internal.api.services.security.authorization;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.PoolOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.annotation.PreDestroy;
import org.kinotic.domain.api.config.OpenFgaProperties;
import org.kinotic.domain.internal.model.security.AuthorizationTuple;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;

/** Calls OpenFGA with explicit store/model routing and bounded request timeouts. */
@Component
@ConditionalOnProperty(prefix = "kinotic.authorization", name = "enabled", havingValue = "true")
public class OpenFgaClient {
    private final WebClient client;
    private final OpenFgaProperties properties;

    public OpenFgaClient(Vertx vertx, OpenFgaProperties properties) {
        if (properties.getPublicationTimeout().isNegative() || properties.getPublicationTimeout().isZero()) throw new IllegalArgumentException("publicationTimeout must be positive");
        if (properties.getCheckTimeout().toMillis() < 1) throw new IllegalArgumentException("checkTimeout must be positive");
        if (properties.getEndpoint().getHost() == null || properties.getEndpoint().getUserInfo() != null || properties.getEndpoint().getQuery() != null || properties.getEndpoint().getFragment() != null
                || !List.of("http", "https").contains(properties.getEndpoint().getScheme())) throw new IllegalArgumentException("Invalid OpenFGA endpoint");
        this.properties = properties;
        this.client = WebClient.create(vertx, new WebClientOptions().setKeepAlive(true).setFollowRedirects(false).setConnectTimeout(1000), new PoolOptions().setHttp1MaxSize(32).setMaxWaitQueueSize(128));
    }

    public Future<JsonObject> request(String path, JsonObject body, Duration timeout) {
        var request = client.postAbs(properties.getEndpoint().toString().replaceAll("/$", "") + path)
                .timeout(timeout.toMillis()).putHeader("Content-Type", "application/json");
        if (properties.getApiToken() != null && !properties.getApiToken().isBlank()) request.bearerTokenAuthentication(properties.getApiToken());
        return request.sendJsonObject(body).map(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("OpenFGA request failed with status " + response.statusCode());
            if (response.body() == null || response.body().length() > 1024 * 1024) throw new IllegalStateException("Invalid OpenFGA response");
            return response.bodyAsJsonObject();
        });
    }

    public Future<Boolean> check(String storeId, String modelId, AuthorizationTuple tuple, List<AuthorizationTuple> context) {
        return request("/stores/" + storeId + "/check", new JsonObject().put("authorization_model_id", modelId)
                .put("consistency", "HIGHER_CONSISTENCY").put("tuple_key", json(tuple))
                .put("contextual_tuples", new JsonObject().put("tuple_keys", array(context))), properties.getCheckTimeout())
                .map(response -> {
                    Object allowed = response.getValue("allowed");
                    if (!(allowed instanceof Boolean value)) throw new IllegalStateException("Incomplete OpenFGA Check response");
                    return value;
                });
    }

    public Future<String> createStore(String name) {
        return request("/stores", new JsonObject().put("name", name), properties.getPublicationTimeout()).map(r -> required(r, "id"));
    }

    public Future<String> writeModel(String store, JsonObject model) {
        return request("/stores/" + store + "/authorization-models", model, properties.getPublicationTimeout())
                .map(r -> required(r, "authorization_model_id"));
    }

    /** Idempotent batches permit recovery after a timeout whose write may already have committed. Requires OpenFGA 1.10+. */
    public Future<Void> reconcile(String store, String model, List<AuthorizationTuple> candidates, List<AuthorizationTuple> desired) {
        var wanted = new java.util.LinkedHashSet<>(desired);
        var removed = new java.util.LinkedHashSet<>(candidates);
        removed.removeAll(wanted);
        return writeBatches(store, model, List.copyOf(removed), false)
                .compose(ignored -> writeBatches(store, model, List.copyOf(wanted), true));
    }
    private Future<Void> writeBatches(String store, String model, List<AuthorizationTuple> tuples, boolean writes) {
        Future<Void> result = Future.succeededFuture();
        for (int offset = 0; offset < tuples.size(); offset += 100) {
            var batch = tuples.subList(offset, Math.min(tuples.size(), offset + 100));
            var body = new JsonObject().put("authorization_model_id", model).put(writes ? "writes" : "deletes",
                    new JsonObject().put("tuple_keys", array(batch)).put(writes ? "on_duplicate" : "on_missing", "ignore"));
            result = result.compose(ignored -> request("/stores/" + store + "/write", body, properties.getPublicationTimeout()).mapEmpty());
        }
        return result;
    }

    private static String required(JsonObject object, String field) {
        String value = object.getString(field);
        if (value == null || !value.matches("[0-9A-HJKMNP-TV-Z]{26}")) throw new IllegalStateException("Invalid OpenFGA " + field);
        return value;
    }
    private static JsonObject json(AuthorizationTuple tuple) {
        return new JsonObject().put("user", tuple.user()).put("relation", tuple.relation()).put("object", tuple.object());
    }
    private static JsonArray array(List<AuthorizationTuple> tuples) {
        return new JsonArray(tuples.stream().map(OpenFgaClient::json).toList());
    }
    @PreDestroy
    public void close() { client.close(); }
}
