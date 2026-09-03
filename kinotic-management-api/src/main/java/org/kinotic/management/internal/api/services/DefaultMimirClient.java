package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.kinotic.management.api.config.ManagementApiProperties;
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

    private final String mimirUrl;

    public DefaultMimirClient(Vertx vertx, ManagementApiProperties properties) {
        super(vertx);
        this.mimirUrl = properties.getMimir().getUrl();
    }

    @Override
    public Future<Buffer> queryRange(String tenant, String query, long start, long end, long step) {
        // The Prometheus API takes its step as a duration
        return get(mimirUrl + QUERY_RANGE_PATH,
                   Map.of("query", query,
                          "start", Long.toString(msToSeconds(start)),
                          "end", Long.toString(msToSeconds(end)),
                          "step", step + "s"),
                   tenant,
                   "Mimir query_range");
    }
}
