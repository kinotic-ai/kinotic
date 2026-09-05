package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.management.api.model.MetricQuery;
import org.kinotic.management.api.model.TraceQuery;
import org.kinotic.management.api.services.MimirClient;
import org.kinotic.management.api.services.TelemetryService;
import org.kinotic.management.api.services.TempoClient;
import org.springframework.stereotype.Component;

/**
 * Default {@link TelemetryService} that reads the tenant the caller may see, per
 * {@link TenantAccess}, via {@link TempoClient} and {@link MimirClient}. Every query is confined
 * to the tenant by the backends themselves, so what a query selects within it is the caller's to
 * decide.
 */
@Component
@RequiredArgsConstructor
public class DefaultTelemetryService implements TelemetryService {

    private final TempoClient tempoClient;
    private final MimirClient mimirClient;
    private final TenantAccess tenantAccess;

    @Override
    public Future<Buffer> searchTraces(TraceQuery query) {
        Validate.notNull(query, "TraceQuery cannot be null");
        Validate.notBlank(query.getQuery(), "query cannot be blank");
        return tempoClient.search(readableTenant(query.getOrganizationId()),
                                  query.getQuery(),
                                  query.getStart(),
                                  query.getEnd(),
                                  query.getLimit());
    }

    @Override
    public Future<Buffer> findTrace(String organizationId, String traceId) {
        Validate.notBlank(traceId, "traceId cannot be blank");
        return tempoClient.findTrace(readableTenant(organizationId), traceId);
    }

    @Override
    public Future<Buffer> queryMetrics(MetricQuery query) {
        Validate.notNull(query, "MetricQuery cannot be null");
        Validate.notBlank(query.getQuery(), "query cannot be blank");
        Validate.isTrue(query.getStep() > 0, "step must be positive");
        return mimirClient.queryRange(readableTenant(query.getOrganizationId()),
                                      query.getQuery(),
                                      query.getStart(),
                                      query.getEnd(),
                                      query.getStep());
    }

    private String readableTenant(String organizationId) {
        return tenantAccess.readableTenant(tenantAccess.currentParticipant(), organizationId);
    }
}
