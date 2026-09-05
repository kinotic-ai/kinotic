package org.kinotic.core.internal.api.support;

import org.kinotic.core.api.security.Participant;

/**
 * A {@link Participant} subtype a service may declare to serve only callers of that scope,
 * the way the platform's services declare {@code OrganizationParticipant} or
 * {@code ApplicationParticipant}.
 */
public interface NarrowParticipant extends Participant {
}
