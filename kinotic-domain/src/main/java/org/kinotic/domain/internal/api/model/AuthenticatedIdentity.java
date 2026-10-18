package org.kinotic.domain.internal.api.model;

import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;

/**
 * What a credential proved: the identity it belongs to, and the participant that identity acts as in the
 * scope the request named.
 */
public record AuthenticatedIdentity(ParticipantIdentity identity, Participant participant) {}
