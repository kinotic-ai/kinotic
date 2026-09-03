package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.SystemParticipant;
import org.kinotic.management.api.model.MetricQuery;
import org.kinotic.management.api.model.TraceQuery;
import org.kinotic.management.api.services.MimirClient;
import org.kinotic.management.api.services.TelemetryService;
import org.kinotic.management.api.services.TempoClient;
import org.springframework.stereotype.Component;

/**
 * Default {@link TelemetryService} that resolves the tenant a caller may read from the participant —
 * an organization participant its own organization's, a system participant any — and reads from
 * that tenant via {@link TempoClient} and {@link MimirClient}. Every query is confined to the tenant
 * by the backends themselves, so what a query selects within it is the caller's to decide.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultTelemetryService implements TelemetryService {

    private final TempoClient tempoClient;
    private final MimirClient mimirClient;
    private final SecurityContext securityContext;

    @Override
    public Future<Buffer> searchTraces(TraceQuery query) {
        Validate.notNull(query, "TraceQuery cannot be null");
        Validate.notBlank(query.getQuery(), "query cannot be blank");
        return tempoClient.search(tenantFor(query.getOrganizationId()),
                                  query.getQuery(),
                                  query.getStart(),
                                  query.getEnd(),
                                  query.getLimit());
    }

    @Override
    public Future<Buffer> findTrace(String organizationId, String traceId) {
        Validate.notBlank(traceId, "traceId cannot be blank");
        return tempoClient.findTrace(tenantFor(organizationId), traceId);
    }

    @Override
    public Future<Buffer> queryMetrics(MetricQuery query) {
        Validate.notNull(query, "MetricQuery cannot be null");
        Validate.notBlank(query.getQuery(), "query cannot be blank");
        Validate.isTrue(query.getStep() > 0, "step must be positive");
        return mimirClient.queryRange(tenantFor(query.getOrganizationId()),
                                      query.getQuery(),
                                      query.getStart(),
                                      query.getEnd(),
                                      query.getStep());
    }

    /**
     * The tenant the caller may read: an organization participant's own organization, whether it
     * named it or not, and for a system participant the organization it named, or the platform's
     * tenant when it named none.
     */
    // Resolved before any asynchronous hop, since SecurityContext reads the calling Vert.x context
    private String tenantFor(String organizationId) {
        Participant participant = securityContext.currentParticipant();
        if (participant == null) {
            throw new IllegalStateException("No Participant is bound to the current Vert.x context");
        }
        String ret;
        if (participant instanceof SystemParticipant) {
            ret = organizationId != null ? organizationId : DefaultLogService.SYSTEM_TENANT;
        } else if (participant instanceof OrganizationParticipant op
                && (organizationId == null || organizationId.equals(op.getOrganizationId()))) {
            ret = op.getOrganizationId();
        } else {
            // Log the mismatch server-side; surface only a generic message to the caller
            log.error("Participant {} may not read the telemetry of organization {}", participant.getId(), organizationId);
            throw new AuthorizationException("Access denied");
        }
        return ret;
    }
}
