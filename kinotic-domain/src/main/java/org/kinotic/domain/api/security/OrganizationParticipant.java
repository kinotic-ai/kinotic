package org.kinotic.domain.api.security;

import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;

/**
 * A participant authenticated against an Organization. {@link #getOrganizationId()} is the
 * id of the owning Organization and is never null. Org-scoped services read this value to
 * filter, route, and validate persistence operations on org-owned data.
 */
public interface OrganizationParticipant extends Participant {

    /**
     * @return the id of the Organization this participant is authenticated under; never null
     */
    String getOrganizationId();

    /**
     * Returns {@code participant} cast to {@link OrganizationParticipant}. Both
     * {@link OrganizationParticipant} and its {@link ApplicationParticipant} subtype
     * satisfy this — APP-scope sessions carry an owning org via inheritance.
     *
     * @throws IllegalStateException if {@code participant} is null
     * @throws AuthorizationException if {@code participant} is not an OrganizationParticipant
     */
    static OrganizationParticipant require(Participant participant) {
        if (participant == null) {
            throw new IllegalStateException("No Participant is bound to the current Vert.x context");
        }
        if (!(participant instanceof OrganizationParticipant op)) {
            throw new AuthorizationException(
                    "OrganizationParticipant required, got " + participant.getClass().getSimpleName());
        }
        return op;
    }
}

