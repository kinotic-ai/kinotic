package org.kinotic.domain.internal.api.rest.mcp;

import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.security.ApplicationParticipant;
import org.kinotic.domain.api.security.OrganizationParticipant;
import org.kinotic.domain.api.security.ScopedParticipant;
import org.kinotic.domain.api.security.SystemParticipant;

/**
 * The directory scope a participant queries tools with: both ids null for a system participant, organization only
 * for an organization participant, and both for an application participant.
 */
record McpCallerScope(String organizationId, String applicationId) {

    static McpCallerScope from(Participant participant) {
        if (!(participant instanceof ScopedParticipant scopedParticipant)) {
            throw new IllegalArgumentException("Unknown participant type " + participant.getClass().getName()
                                                       + ", no directory scope exists for it");
        }
        McpCallerScope ret = switch (scopedParticipant) {
            case SystemParticipant _ -> new McpCallerScope(null, null);
            case ApplicationParticipant applicationParticipant ->
                    new McpCallerScope(applicationParticipant.getOrganizationId(),
                                       applicationParticipant.getApplicationId());
            case OrganizationParticipant organizationParticipant ->
                    new McpCallerScope(organizationParticipant.getOrganizationId(), null);
        };
        return ret;
    }
}
