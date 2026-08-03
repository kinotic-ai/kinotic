package org.kinotic.domain.api.security;

import org.kinotic.core.api.security.Participant;

/**
 * The scope coordinates of a {@link ScopedParticipant}, as a value usable for filtering,
 * routing, and directory queries. Which ids are present follows from the participant's
 * scope layer: SYSTEM carries none, ORGANIZATION carries {@code organizationId} only,
 * APPLICATION carries {@code organizationId} and {@code applicationId} (and
 * {@code tenantId} when the application is multi-tenant).
 *
 * @param organizationId the owning organization, or {@code null} for SYSTEM scope
 * @param applicationId  the application, or {@code null} unless APPLICATION scope
 * @param tenantId       the tenant slice of the application's end-user data, or {@code null}
 *                       when not applicable
 */
public record ParticipantScope(String organizationId, String applicationId, String tenantId) {

    /**
     * Resolves the scope coordinates of the given participant.
     *
     * @param participant the authenticated participant
     * @return the participant's scope coordinates
     * @throws IllegalArgumentException if the participant is not a {@link ScopedParticipant}
     */
    public static ParticipantScope of(Participant participant) {
        if (!(participant instanceof ScopedParticipant scopedParticipant)) {
            throw new IllegalArgumentException("Unknown participant type " + participant.getClass().getName()
                                                       + ", no scope exists for it");
        }
        return scopedParticipant.getScope();
    }
}
