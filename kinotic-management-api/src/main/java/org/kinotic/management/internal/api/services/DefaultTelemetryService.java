package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.utils.ZoneUtil;
import org.kinotic.management.api.model.InvocationMetrics;
import org.kinotic.management.api.model.InvocationOutcome;
import org.kinotic.management.api.model.MetricQuery;
import org.kinotic.management.api.model.TraceQuery;
import org.kinotic.management.api.model.TrafficQuery;
import org.kinotic.management.api.services.MimirClient;
import org.kinotic.management.api.services.TelemetryService;
import org.kinotic.management.api.services.TempoClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default {@link TelemetryService} that reads the tenant the caller may see, per
 * {@link TenantAccess}, via {@link TempoClient} and {@link MimirClient}. Every query is confined
 * to the tenant by the backends themselves, so what a query selects within it is the caller's to
 * decide. Traffic is the exception: the gateway records every organization's in the platform
 * tenant, so its expression is built here, selecting only the organization the caller may read.
 */
@Component
@RequiredArgsConstructor
public class DefaultTelemetryService implements TelemetryService {

    // The outcomes that count as failed, as one label matcher
    private static final String FAILED_MATCHER = InvocationMetrics.OUTCOME + "=~\""
            + Arrays.stream(InvocationOutcome.values())
                    .filter(InvocationOutcome::isFailure)
                    .map(InvocationOutcome::label)
                    .collect(Collectors.joining("|"))
            + "\"";

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

    @Override
    public Future<Buffer> queryTraffic(TrafficQuery query) {
        Validate.notNull(query, "TrafficQuery cannot be null");
        Validate.notNull(query.getSignal(), "signal cannot be null");
        Validate.isTrue(query.getStep() > 0, "step must be positive");
        Validate.isTrue(query.getApplicationId() == null || query.getOrganizationId() != null,
                        "an application's traffic is read within its organization");
        // The ids are written into the expression, so nothing but a zone label gets that far
        if (query.getOrganizationId() != null) {
            ZoneUtil.validateLabel(query.getOrganizationId());
        }
        if (query.getApplicationId() != null) {
            ZoneUtil.validateLabel(query.getApplicationId());
        }
        tenantAccess.requireReadable(tenantAccess.currentParticipant(), query.getOrganizationId());
        return mimirClient.queryRange(TenantAccess.SYSTEM_TENANT,
                                      trafficExpression(query),
                                      query.getStart(),
                                      query.getEnd(),
                                      query.getStep());
    }

    private String readableTenant(String organizationId) {
        return tenantAccess.readableTenant(tenantAccess.currentParticipant(), organizationId);
    }

    private static String trafficExpression(TrafficQuery query) {
        // A few steps per window so each point averages several samples, and never under a minute:
        // the window the clients take over their own range queries
        String window = "[" + Math.max(60, 4 * query.getStep()) + "s]";
        String calls = "sum(rate(" + InvocationMetrics.SERIES_NAME + "_count" + invocationSelector(query) + window + "))";
        return switch (query.getSignal()) {
            case REQUESTS -> calls;
            case ERRORS -> "(sum(rate(" + InvocationMetrics.SERIES_NAME + "_count" + invocationSelector(query, FAILED_MATCHER) + window + "))"
                           + " or vector(0)) / " + calls;
            case LATENCY_P95 -> "histogram_quantile(0.95, sum by (le) (rate("
                                + InvocationMetrics.SERIES_NAME + "_bucket" + invocationSelector(query) + window + ")))";
        };
    }

    // The label matchers selecting the query's organization and application, then the extra ones
    private static String invocationSelector(TrafficQuery query, String... extra) {
        List<String> matchers = new ArrayList<>();
        if (query.getOrganizationId() != null) {
            matchers.add(InvocationMetrics.ORGANIZATION_ID + "=\"" + query.getOrganizationId() + "\"");
        }
        if (query.getApplicationId() != null) {
            matchers.add(InvocationMetrics.APPLICATION_ID + "=\"" + query.getApplicationId() + "\"");
        }
        matchers.addAll(List.of(extra));
        return matchers.isEmpty() ? "" : "{" + String.join(", ", matchers) + "}";
    }
}
