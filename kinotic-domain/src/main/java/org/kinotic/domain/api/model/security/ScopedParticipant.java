package org.kinotic.domain.api.model.security;

import org.kinotic.core.api.security.Participant;

/**
 * A {@link Participant} whose authority is scoped to one layer of the platform. The permitted
 * subtypes are the complete set of scopes a participant may hold, so a {@code switch} over this
 * type is exhaustive: code that maps every scope to a value gains a compile error when a scope is
 * added, rather than reaching a runtime branch that has no answer for it.
 * <p>
 * Reserve such switches for decisions that differ per layer (see {@link ZoneRules}); code that
 * only needs the participant's scope ids should read {@link #getScope()} instead of matching
 * the subtypes.
 */
public sealed interface ScopedParticipant extends Participant
        permits SystemParticipant, OrganizationParticipant, ApplicationParticipant {

    /**
     * @return this participant's scope coordinates; each subtype supplies the projection
     *         matching its layer
     */
    ParticipantScope getScope();
}
