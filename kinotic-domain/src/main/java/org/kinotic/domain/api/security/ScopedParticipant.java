package org.kinotic.domain.api.security;

import org.kinotic.core.api.security.Participant;

/**
 * A {@link Participant} whose authority is scoped to one layer of the platform. The permitted
 * subtypes are the complete set of scopes a participant may hold, so a {@code switch} over this
 * type is exhaustive: code that maps every scope to a value gains a compile error when a scope is
 * added, rather than reaching a runtime branch that has no answer for it.
 */
public sealed interface ScopedParticipant extends Participant
        permits SystemParticipant, OrganizationParticipant, ApplicationParticipant {
}
