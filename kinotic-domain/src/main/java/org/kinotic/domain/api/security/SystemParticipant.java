package org.kinotic.domain.api.security;

import org.kinotic.core.api.security.Participant;

/**
 * A participant authenticated against the platform SYSTEM scope. Platform operators with
 * no organization or application context; carries no scope-id beyond the participant's
 * own {@code id}.
 */
public interface SystemParticipant extends Participant {
}
