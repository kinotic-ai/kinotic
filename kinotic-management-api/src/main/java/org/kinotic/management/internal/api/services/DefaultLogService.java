package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.management.api.model.LogQuery;
import org.kinotic.management.api.services.LogService;
import org.kinotic.management.api.services.LokiClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Default {@link LogService} that reads from the Loki tenant of the organization named by each call,
 * provided {@link TenantAccess} admits the caller to it.
 */
@Component
@RequiredArgsConstructor
public class DefaultLogService implements LogService {

    private final LokiClient lokiClient;
    private final TenantAccess tenantAccess;

    @Override
    public Flux<Buffer> tail(String organizationId, String workloadId) {
        Flux<Buffer> ret;
        try {
            Validate.notBlank(workloadId, "workloadId cannot be blank");
            // Authorization runs before subscription: the participant is read from the calling Vert.x context
            String tenant = tenantAccess.readableTenant(tenantAccess.currentParticipant(), organizationId);
            ret = lokiClient.tail(tenant, logQlFor(workloadId));
        } catch (Exception e) {
            ret = Flux.error(e);
        }
        return ret;
    }

    @Override
    public Future<Buffer> history(LogQuery query) {
        Future<Buffer> ret;
        try {
            Validate.notNull(query, "LogQuery cannot be null");
            Validate.notBlank(query.getWorkloadId(), "workloadId cannot be blank");
            String tenant = tenantAccess.readableTenant(tenantAccess.currentParticipant(), query.getOrganizationId());
            ret = lokiClient.queryRange(tenant,
                                        logQlFor(query.getWorkloadId()),
                                        query.getStart(),
                                        query.getEnd(),
                                        query.getLimit());
        } catch (Exception e) {
            ret = Future.failedFuture(e);
        }
        return ret;
    }

    // The id is quoted into a label matcher, so a quote or backslash in it cannot widen the selector
    private static String logQlFor(String workloadId) {
        return "{workload_id=\"" + workloadId.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }
}
