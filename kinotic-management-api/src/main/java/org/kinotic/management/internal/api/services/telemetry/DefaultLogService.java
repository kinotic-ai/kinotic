package org.kinotic.management.internal.api.services.telemetry;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.management.api.model.telemetry.LogQuery;
import org.kinotic.management.api.model.telemetry.ServerLogQuery;
import org.kinotic.management.api.model.telemetry.TelemetryTenant;
import org.kinotic.management.api.services.telemetry.LogService;
import org.kinotic.management.api.services.telemetry.LokiClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default {@link LogService} that reads a workload's logs from the Loki tenant of the organization named
 * by each call, and a server's from the platform's tenant, provided {@link TenantAccess} admits the caller.
 */
@Component
@RequiredArgsConstructor
public class DefaultLogService implements LogService {

    private final LokiClient lokiClient;
    private final TenantAccess tenantAccess;

    @Override
    public Flux<Buffer> tail(String organizationId, String workloadId, long start) {
        Flux<Buffer> ret;
        try {
            Validate.notBlank(workloadId, "workloadId cannot be blank");
            ret = tailOf(organizationId, TelemetryTenant.workloadLogSelector(workloadId), start);
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
            ret = tenantAccess.readableTenant(tenantAccess.currentParticipant(), query.getOrganizationId())
                              .compose(tenant -> lokiClient.queryRange(tenant,
                                                                       TelemetryTenant.workloadLogSelector(query.getWorkloadId()),
                                                                       query.getStart(),
                                                                       query.getEnd(),
                                                                       query.getLimit()));
        } catch (Exception e) {
            ret = Future.failedFuture(e);
        }
        return ret;
    }

    @Override
    public Flux<Buffer> tailServer(String telemetryServiceName, String telemetryServiceInstanceId, long start) {
        Flux<Buffer> ret;
        try {
            // the platform's tenant, which admits a platform participant alone
            ret = tailOf(null, TelemetryTenant.serverLogSelector(telemetryServiceName, telemetryServiceInstanceId), start);
        } catch (Exception e) {
            ret = Flux.error(e);
        }
        return ret;
    }

    @Override
    public Future<Buffer> serverHistory(ServerLogQuery query) {
        Future<Buffer> ret;
        try {
            Validate.notNull(query, "ServerLogQuery cannot be null");
            String selector = TelemetryTenant.serverLogSelector(query.getTelemetryServiceName(),
                                                                query.getTelemetryServiceInstanceId());
            ret = tenantAccess.readableTenant(tenantAccess.currentParticipant(), null)
                              .compose(tenant -> lokiClient.queryRange(tenant, selector, query.getStart(), query.getEnd(), query.getLimit()));
        } catch (Exception e) {
            ret = Future.failedFuture(e);
        }
        return ret;
    }

    // Authorization starts before subscription: the participant is read from the calling Vert.x context, and
    // the tenant it may read is asked for at once
    private Flux<Buffer> tailOf(String organizationId, String selector, long start) {
        Future<String> tenant = tenantAccess.readableTenant(tenantAccess.currentParticipant(), organizationId);
        return Mono.fromCompletionStage(tenant.toCompletionStage())
                   .flatMapMany(readable -> lokiClient.tail(readable, selector, start));
    }
}
