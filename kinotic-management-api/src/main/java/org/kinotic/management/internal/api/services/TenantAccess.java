package org.kinotic.management.internal.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.SystemParticipant;
import org.springframework.stereotype.Component;

/**
 * Decides which telemetry tenant a caller may read. Every organization's workload logs,
 * traces, and metrics live in a tenant named by the organization id, and the platform's own
 * in {@link #SYSTEM_TENANT}; an organization participant reads its own organization's tenant
 * alone, a system participant any.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantAccess {

    /**
     * Tenant that receives the logs, traces, and metrics of platform workloads with no organization
     * (SYSTEM scope). Must match the tenant the vm-manager's AlloyManager ships them under;
     * organization ids can never take this value — ids beginning with "kinotic" are reserved for
     * the platform.
     */
    public static final String SYSTEM_TENANT = "kinotic-system";

    private final SecurityContext securityContext;

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
     * @return the tenant to query
     * @throws AuthorizationException when the participant may not read that tenant
     */
    public String readableTenant(Participant participant, String organizationId) {
        String ret;
        if (participant instanceof SystemParticipant) {
            ret = organizationId != null ? organizationId : SYSTEM_TENANT;
        } else if (participant instanceof OrganizationParticipant op && op.getOrganizationId().equals(organizationId)) {
            ret = organizationId;
        } else {
            // Log the mismatch server-side; surface only a generic message to the caller
            log.error("Participant {} may not read the telemetry of organization {}", participant.getId(), organizationId);
            throw new AuthorizationException("Access denied");
        }
        return ret;
    }
}
