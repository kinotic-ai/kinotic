package org.kinotic.management.api.services.telemetry;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.telemetry.LogQuery;
import org.kinotic.management.api.model.telemetry.ServerLogQuery;
import reactor.core.publisher.Flux;

/**
 * Streams and queries the logs of workloads by the organization they ran for, and of the platform's
 * own servers. For workloads, an organization participant reads its own organization's, a system
 * participant reads any organization's, or the platform's own when it names no organization. A
 * workload's logs outlive the workload, so a destroyed one's are read the same way. Only a system
 * participant reads a server's logs. Every method returns the raw Loki response bytes; the caller
 * parses Loki's wire format.
 */
@Publish //FIXME: figure out how to provide an McpTool that returns a flux, or add an exclusion to the McpTool annotation.
public interface LogService {

    /**
     * Opens a live tail of the given workload's logs. Each emitted element is a raw Loki tail
     * frame, and the stream stays open until the caller unsubscribes.
     *
     * @param organizationId the organization the workload runs for; null for the platform's own,
     *        which only a system participant may read
     * @param workloadId the id of the workload to follow
     * @return a {@link Flux} emitting raw Loki tail frames
     */
    Flux<Buffer> tail(String organizationId, String workloadId);

    /**
     * Returns a workload's historical logs for the given time range.
     *
     * @param query the {@link LogQuery} naming the organization, workload, time range, and limit
     * @return a {@link Future} emitting the raw Loki {@code query_range} response
     */
    Future<Buffer> history(LogQuery query);

    /**
     * Opens a live tail of a platform server's logs, one node's or every node's together. Each
     * emitted element is a raw Loki tail frame, and the stream stays open until the caller
     * unsubscribes.
     *
     * @param telemetryServiceName the service name that labels the server's logs, as its cluster
     *        nodes report it
     * @param telemetryServiceInstanceId the service instance id that labels one node's logs, as
     *        that cluster node reports it; null for every node of the server
     * @return a {@link Flux} emitting raw Loki tail frames
     */
    Flux<Buffer> tailServer(String telemetryServiceName, String telemetryServiceInstanceId);

    /**
     * Returns a platform server's historical logs, one node's or every node's together, for the
     * given time range.
     *
     * @param query the {@link ServerLogQuery} naming the server, the node, the time range, and the limit
     * @return a {@link Future} emitting the raw Loki {@code query_range} response
     */
    Future<Buffer> serverHistory(ServerLogQuery query);
}
