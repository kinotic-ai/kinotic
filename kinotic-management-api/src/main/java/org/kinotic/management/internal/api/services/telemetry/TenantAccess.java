package org.kinotic.management.internal.api.services.telemetry;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.SystemParticipant;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.model.telemetry.TelemetryTenant;
import org.springframework.stereotype.Component;

import static org.kinotic.authz.api.services.AuthzStoreService.PLATFORM;

/**
 * Decides which telemetry tenant a caller may read. Every organization's workload logs,
 * traces, and metrics live in a tenant named by the organization id, and the platform's own
 * in {@link TelemetryTenant#SYSTEM}; an organization participant reads its own organization's
 * tenant alone, a platform participant any tenant once granted the telemetry of organizations on
 * the platform.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantAccess {

    // the permission a platform grant must carry to read any tenant: the one the telemetry services require
    // of an organization's members, held on the platform, which every organization is on
    private static final String READS_TELEMETRY = AuthzUtil.permissionName(AuthzUtil.ORGANIZATION_TYPE, AuthzUtil.CAN_VIEW_TELEMETRY);

    private final SecurityContext securityContext;
    private final AuthzStoreService stores;
    private final RelationshipService relationships;

    /**
     * The participant making the current call.
     *
     * @throws IllegalStateException when the calling Vert.x context carries no participant
     */
    public Participant currentParticipant() {
        Participant participant = securityContext.currentParticipant();
        if (participant == null) {
            throw new IllegalStateException("No Participant is bound to the current Vert.x context");
        }
        return participant;
    }

    /**
     * The tenant holding the given organization's telemetry — the platform's when it has none —
     * provided the participant may read it.
     *
     * @param participant    the caller
     * @param organizationId the organization whose telemetry is wanted, or null for the platform's
     * @return the tenant to query, or a failure with {@link AuthorizationException} when the participant may
     *         not read that tenant
     */
    public Future<String> readableTenant(Participant participant, String organizationId) {
        Future<String> ret;
        if (participant instanceof SystemParticipant) {
            RelationshipTuple reads = new RelationshipTuple(DomainUtil.authzUser(participant), READS_TELEMETRY,
                                                            AuthzUtil.object(AuthzUtil.PLATFORM_TYPE, AuthzUtil.PLATFORM_OBJECT_ID));
            ret = stores.modelId(PLATFORM)
                        .compose(modelId -> relationships.check(PLATFORM, modelId, reads, Consistency.MINIMIZE_LATENCY))
                        .map(allowed -> {
                            if (!allowed) {
                                throw denied(participant, organizationId);
                            }
                            return TelemetryTenant.of(organizationId);
                        });
        } else if (participant instanceof OrganizationParticipant op && op.getOrganizationId().equals(organizationId)) {
            ret = Future.succeededFuture(organizationId);
        } else {
            ret = Future.failedFuture(denied(participant, organizationId));
        }
        return ret;
    }

    // The mismatch is logged server-side; the caller gets a generic message
    private static AuthorizationException denied(Participant participant, String organizationId) {
        log.error("Participant {} may not read the telemetry of organization {}", participant.getId(), organizationId);
        return new AuthorizationException("Access denied");
    }
}
