package org.kinotic.domain.api.security;

import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;

/**
 * A participant authenticated against an Application. Extends {@link OrganizationParticipant}
 * because every Application belongs to an Organization, so APP-scope sessions intrinsically
 * carry the owning Organization's id as well.
 */
public interface ApplicationParticipant extends OrganizationParticipant {

    /**
     * @return the id of the Application this participant is authenticated under; never null
     */
    String getApplicationId();

    /**
     * @return the tenant slice of the Application's end-user data this participant belongs
     *         to, or null when the Application is not multi-tenant
     */
    String getTenantId();

    /**
     * Returns {@code participant} cast to {@link ApplicationParticipant}.
     *
     * @throws IllegalStateException if {@code participant} is null
     * @throws AuthorizationException if {@code participant} is not an ApplicationParticipant
     */
    static ApplicationParticipant require(Participant participant) {
        if (participant == null) {
            throw new IllegalStateException("No Participant is bound to the current Vert.x context");
        }
        if (!(participant instanceof ApplicationParticipant ap)) {
            throw new AuthorizationException(
                    "ApplicationParticipant required, got " + participant.getClass().getSimpleName());
        }
        return ap;
    }
}

