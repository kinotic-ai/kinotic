package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.Participant;
import org.kinotic.management.api.model.LogQuery;
import org.kinotic.management.api.repositories.WorkloadRepository;
import org.kinotic.management.api.services.LogService;
import org.kinotic.management.api.services.LokiClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default {@link LogService} that authorizes access through the workload record — an
 * organization participant may only view its own organization's workloads — and reads
 * from the organization's Loki tenant via {@link LokiClient}.
 */
@Component
@RequiredArgsConstructor
public class DefaultLogService implements LogService {

    private final LokiClient lokiClient;
    private final TenantAccess tenantAccess;
    private final WorkloadRepository workloadRepository;

    @Override
    public Flux<Buffer> tail(String workloadId) {
        // Authorization starts before subscription: the participant is read from the calling Vert.x context
        Future<String> tenant = authorizedTenant(workloadId);
        return Mono.fromCompletionStage(tenant.toCompletionStage())
                   .flatMapMany(t -> lokiClient.tail(t, logQlFor(workloadId)));
    }

    @Override
    public Future<Buffer> history(LogQuery query) {
        Validate.notNull(query, "LogQuery cannot be null");
        return authorizedTenant(query.getWorkloadId())
                .compose(tenant -> lokiClient.queryRange(tenant,
                                                         logQlFor(query.getWorkloadId()),
                                                         query.getStart(),
                                                         query.getEnd(),
                                                         query.getLimit()));
    }

    /**
     * The tenant holding the workload's logs, provided the caller may read it.
     */
    private Future<String> authorizedTenant(String workloadId) {
        Validate.notBlank(workloadId, "workloadId cannot be blank");
        // Read before the repository hop, which completes off the calling Vert.x context
        Participant participant = tenantAccess.currentParticipant();
        return workloadRepository.findById(workloadId).map(workload -> {
            if (workload == null) {
                throw new IllegalArgumentException("Workload not found: " + workloadId);
            }
            return tenantAccess.readableTenant(participant, workload.getOrganizationId());
        });
    }

    // The id named the record just found, so it is safe to embed in LogQL
    private static String logQlFor(String workloadId) {
        return "{workload_id=\"" + workloadId + "\"}";
    }
}
