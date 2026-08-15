package org.kinotic.os.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.model.log.LogQuery;
import reactor.core.publisher.Flux;

/**
 * Streams and queries the logs of workloads the authenticated participant may view: an
 * organization participant sees its own organization's workloads, a system participant sees any.
 * Both methods return the raw Loki response bytes; the caller parses Loki's wire format.
 */
@Publish //FIXME: figure out how to provide an McpTool that returns a flux, or add an exclusion to the McpTool annotation.
public interface LogService {

    /**
     * Opens a live tail of the given workload's logs. Each emitted element is a raw Loki tail
     * frame, and the stream stays open until the caller unsubscribes.
     *
     * @param workloadId the id of the workload to follow
     * @return a {@link Flux} emitting raw Loki tail frames
     */
    Flux<Buffer> tail(String workloadId);

    /**
     * Returns a workload's historical logs for the given time range.
     *
     * @param query the {@link LogQuery} naming the workload, time range, and limit
     * @return a {@link Future} emitting the raw Loki {@code query_range} response
     */
    Future<Buffer> history(LogQuery query);
}
