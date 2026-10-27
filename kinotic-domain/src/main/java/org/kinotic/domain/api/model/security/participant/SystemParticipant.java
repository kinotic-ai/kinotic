package org.kinotic.domain.api.model.security.participant;

/**
 * A participant authenticated against the SYSTEM scope. System users with
 * no organization or application context; carries no scope-id beyond the participant's
 * own {@code id}.
 */
public non-sealed interface SystemParticipant extends ScopedParticipant {

    @Override
    default ParticipantScope getScope() {
        return new ParticipantScope(null, null, null);
    }
}
