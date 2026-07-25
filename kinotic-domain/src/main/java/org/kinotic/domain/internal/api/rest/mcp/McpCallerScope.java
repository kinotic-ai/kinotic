package org.kinotic.domain.internal.api.rest.mcp;

import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.security.ApplicationParticipant;
import org.kinotic.domain.api.security.OrganizationParticipant;
import org.kinotic.domain.api.security.SystemParticipant;

/**
 * The directory scope a participant queries tools with: both ids null for a system participant, organization only
 * for an organization participant, and both for an application participant.
 */
record McpCallerScope(String organizationId, String applicationId) {

    static McpCallerScope from(Participant participant) {
        McpCallerScope ret;
        if (participant instanceof SystemParticipant) {
            ret = new McpCallerScope(null, null);
        } else if (participant instanceof ApplicationParticipant applicationParticipant) {
            ret = new McpCallerScope(applicationParticipant.getOrganizationId(),
                                     applicationParticipant.getApplicationId());
        } else if (participant instanceof OrganizationParticipant organizationParticipant) {
            ret = new McpCallerScope(organizationParticipant.getOrganizationId(), null);
        } else {
            throw new IllegalArgumentException("Unknown participant type " + participant.getClass().getName()
                                                       + ", no directory scope exists for it");
        }
        return ret;
    }
}
