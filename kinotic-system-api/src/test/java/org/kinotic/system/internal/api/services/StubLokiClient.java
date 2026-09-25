package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.kinotic.management.api.services.LokiClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Stand-in for the Loki transport that records every delete request, the one call workload
 * orchestration makes.
 */
public class StubLokiClient implements LokiClient {

    /** The tenant of the last delete, or null while none was asked for. */
    public String deletedTenant;

    /** The selector of the last delete, or null while none was asked for. */
    public String deletedQuery;

    /** Every delete asked for, as "tenant selector", in order. */
    public final List<String> deletes = new ArrayList<>();

    @Override
    public Future<Buffer> queryRange(String tenant, String query, long start, long end, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Flux<Buffer> tail(String tenant, String query) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Void> delete(String tenant, String query, long start, long end) {
        deletedTenant = tenant;
        deletedQuery = query;
        deletes.add(tenant + " " + query);
        return Future.succeededFuture();
    }
}
