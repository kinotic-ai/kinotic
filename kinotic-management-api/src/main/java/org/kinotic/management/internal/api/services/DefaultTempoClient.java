package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.apache.commons.lang3.Validate;
import org.kinotic.management.api.config.TempoProperties;
import org.kinotic.management.api.services.TempoClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Vert.x-backed {@link TempoClient} over Tempo's {@code /api/search} and {@code /api/traces} endpoints.
 */
@Component
public class DefaultTempoClient extends AbstractTenantScopedClient implements TempoClient {

    private static final String SEARCH_PATH = "/api/search";
    private static final String TRACES_PATH = "/api/traces/";

    // A trace id is at most 128 bits of hex; anything else would be a path of its own
    private static final Pattern TRACE_ID = Pattern.compile("^[0-9a-fA-F]{1,32}$");

    private final TempoProperties tempoProperties;

    public DefaultTempoClient(Vertx vertx, TempoProperties tempoProperties) {
        super(vertx);
        this.tempoProperties = tempoProperties;
    }

    @Override
    public Future<Buffer> search(String tenant, String query, long start, long end, int limit) {
        // Tempo's search takes its range in epoch seconds
        return get(tempoProperties.getUrl() + SEARCH_PATH,
                   Map.of("q", query,
                          "start", Long.toString(start / 1000),
                          "end", Long.toString(end / 1000),
                          "limit", Integer.toString(limit)),
                   tenant,
                   "Tempo search");
    }

    @Override
    public Future<Buffer> findTrace(String tenant, String traceId) {
        Validate.isTrue(TRACE_ID.matcher(traceId).matches(), "traceId must be a hex trace id");
        return get(tempoProperties.getUrl() + TRACES_PATH + traceId, Map.of(), tenant, "Tempo trace lookup");
    }
}
