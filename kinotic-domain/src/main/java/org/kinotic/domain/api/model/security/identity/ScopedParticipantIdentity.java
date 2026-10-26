package org.kinotic.domain.api.model.security.identity;

import org.kinotic.domain.api.model.security.participant.ParticipantScope;

/**
 * A {@link ParticipantIdentity} that acts in the one scope it carries, encoded by which of its ids are set:
 * <ul>
 *   <li>{@code organizationId} and {@code applicationId} null → SYSTEM</li>
 *   <li>{@code organizationId} set, {@code applicationId} null → ORGANIZATION</li>
 *   <li>both set → APPLICATION, of an application owned by the organization</li>
 * </ul>
 * An {@link OrganizationParticipantIdentity} is always of the ORGANIZATION scope and an
 * {@link ApplicationParticipantIdentity} of the APPLICATION scope; a delegate or a machine may be of any.
 */
public sealed interface ScopedParticipantIdentity
        permits OrganizationParticipantIdentity, ApplicationParticipantIdentity, DelegatingParticipantIdentity, MachineParticipantIdentity {

    /** The scope this identity acts in. */
    ParticipantScope scope();
}
