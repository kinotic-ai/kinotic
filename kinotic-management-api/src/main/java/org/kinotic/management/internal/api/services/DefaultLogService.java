package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.log.LogQuery;
import org.kinotic.domain.api.model.workload.Workload;
import org.kinotic.domain.api.model.security.OrganizationParticipant;
import org.kinotic.domain.api.model.security.SystemParticipant;
import org.kinotic.domain.api.services.LokiClient;
import org.kinotic.domain.internal.api.repositories.WorkloadRepository;
import org.kinotic.management.api.services.LogService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default {@link LogService} that authorizes access through the workload record — an
 * organization participant may only view its own organization's workloads — and reads
 * from the organization's Loki tenant via {@link LokiClient}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultLogService implements LogService {

    /**
     * Loki tenant that receives logs for platform workloads with no organization (SYSTEM scope).
     * Must match the tenant the vm-manager's AlloyManager ships those logs under; organization
     * ids can never take this value — ids beginning with "kinotic" are reserved for the platform.
     */
    public static final String SYSTEM_LOG_TENANT = "kinotic-system";

    private final LokiClient lokiClient;
    private final SecurityContext securityContext;
    private final WorkloadRepository workloadRepository;

    @Override
    public Flux<Buffer> tail(String workloadId) {
        // Authorization starts before subscription: SecurityContext reads the calling Vert.x context
        Future<Workload> authorized = authorizedWorkload(workloadId);
        return Mono.fromCompletionStage(authorized.toCompletionStage())
                   .flatMapMany(workload -> lokiClient.tail(tenantFor(workload), logQlFor(workload)));
    }

    @Override
    public Future<Buffer> history(LogQuery query) {
        Validate.notNull(query, "LogQuery cannot be null");
        return authorizedWorkload(query.getWorkloadId())
                .compose(workload -> lokiClient.queryRange(tenantFor(workload),
                                                           logQlFor(workload),
                                                           query.getStart(),
                                                           query.getEnd(),
                                                           query.getLimit()));
    }

    private Future<Workload> authorizedWorkload(String workloadId) {
        Validate.notBlank(workloadId, "workloadId cannot be blank");
        Participant participant = securityContext.currentParticipant();
        if (participant == null) {
            throw new IllegalStateException("No Participant is bound to the current Vert.x context");
        }
        return workloadRepository.findById(workloadId).map(workload -> {
            if (workload == null) {
                throw new IllegalArgumentException("Workload not found: " + workloadId);
            }
            if (participant instanceof SystemParticipant) {
                return workload;
            }
            if (participant instanceof OrganizationParticipant op
                    && op.getOrganizationId().equals(workload.getOrganizationId())) {
                return workload;
            }
            // Log the mismatch server-side; surface only a generic message to the caller
            log.error("Participant {} may not view logs of workload {} (workload org={})",
                      participant.getId(), workloadId, workload.getOrganizationId());
            throw new AuthorizationException("Access denied");
        });
    }

    private static String tenantFor(Workload workload) {
        return workload.getOrganizationId() != null ? workload.getOrganizationId()
                                                    : SYSTEM_LOG_TENANT;
    }

    // The id comes from the persisted workload record, so it is safe to embed in LogQL
    private static String logQlFor(Workload workload) {
        return "{workload_id=\"" + workload.getId() + "\"}";
    }
}
