package org.kinotic.management.internal.api.services.telemetry;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.management.api.model.telemetry.MetricQuery;
import org.kinotic.management.api.model.telemetry.TraceQuery;
import org.kinotic.management.api.services.telemetry.MimirClient;
import org.kinotic.management.api.services.telemetry.TelemetryService;
import org.kinotic.management.api.services.telemetry.TempoClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Default {@link TelemetryService} that reads the tenant the caller may see, per
 * {@link TenantAccess}, via {@link TempoClient} and {@link MimirClient}. Every query is confined
 * to the tenant by the backends themselves, so what a query selects within it is the caller's to
 * decide.
 */
@Component
@RequiredArgsConstructor
public class DefaultTelemetryService implements TelemetryService {

    private static final long MAX_TRACE_LOOKUP_MS = Duration.ofDays(1).toMillis();

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
    public Future<Buffer> findTrace(String organizationId, String traceId, long start, long end) {
        Validate.notBlank(traceId, "traceId cannot be blank");
        // Tempo reads a start of 0 seconds as no lower bound, which would scan every block it keeps
        Validate.isTrue(start >= 1_000, "start must be at least 1000");
        Validate.isTrue(start <= end, "start must not be after end");
        Validate.isTrue(end - start <= MAX_TRACE_LOOKUP_MS, "the time range must span at most a day");
        return tempoClient.findTrace(readableTenant(organizationId), traceId, start, end);
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
