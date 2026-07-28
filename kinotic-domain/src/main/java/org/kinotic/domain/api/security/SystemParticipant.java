package org.kinotic.domain.api.security;

/**
 * A participant authenticated against the platform SYSTEM scope. Platform operators with
 * no organization or application context; carries no scope-id beyond the participant's
 * own {@code id}.
 */
public non-sealed interface SystemParticipant extends ScopedParticipant {
}
