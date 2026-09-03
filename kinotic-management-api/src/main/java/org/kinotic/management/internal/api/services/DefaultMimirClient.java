package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.kinotic.management.api.config.MimirProperties;
import org.kinotic.management.api.services.MimirClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Vert.x-backed {@link MimirClient} over the Prometheus {@code query_range} endpoint Mimir serves under
 * {@code /prometheus}.
 */
@Component
public class DefaultMimirClient extends AbstractTenantScopedClient implements MimirClient {

    private static final String QUERY_RANGE_PATH = "/prometheus/api/v1/query_range";

    private final MimirProperties mimirProperties;

    public DefaultMimirClient(Vertx vertx, MimirProperties mimirProperties) {
        super(vertx);
        this.mimirProperties = mimirProperties;
    }

    @Override
    public Future<Buffer> queryRange(String tenant, String query, long start, long end, long step) {
        // The Prometheus API takes its range in epoch seconds and its step as a duration
        return get(mimirProperties.getUrl() + QUERY_RANGE_PATH,
                   Map.of("query", query,
                          "start", Long.toString(start / 1000),
                          "end", Long.toString(end / 1000),
                          "step", step + "s"),
                   tenant,
                   "Mimir query_range");
    }
}
