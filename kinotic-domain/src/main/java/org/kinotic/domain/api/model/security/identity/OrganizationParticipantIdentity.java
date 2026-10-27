package org.kinotic.domain.api.model.security.identity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;

/**
 * A member of one organization, signing in at that organization's login and acting as an
 * {@link org.kinotic.domain.api.model.security.participant.OrganizationParticipant} of it; unique by email within the
 * organization. A person in several organizations is a separate user of each, with its own sign-in methods.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class OrganizationParticipantIdentity extends UserParticipantIdentity implements ScopedParticipantIdentity {

    /** The organization whose member this is. */
    private String organizationId;

    @Override
    public ParticipantScope scope() {
        return new ParticipantScope(organizationId, null, null);
    }

    @Override
    public ParticipantIdentityType getType() {
        return ParticipantIdentityType.ORGANIZATION_USER;
    }
}
