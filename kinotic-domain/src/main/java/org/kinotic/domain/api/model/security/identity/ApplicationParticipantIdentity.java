package org.kinotic.domain.api.model.security.identity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;

/**
 * A user of one application's own user base, signing in at that application's login and acting as an
 * {@link org.kinotic.domain.api.model.security.participant.ApplicationParticipant}; unique by email within the
 * application.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class ApplicationParticipantIdentity extends UserParticipantIdentity implements ScopedParticipantIdentity {

    /** The organization that owns the user's application. */
    private String organizationId;

    /** The application whose user this is. */
    private String applicationId;

    /**
     * The tenant of the application this user belongs to: one of its own, which the system gives it, or one its
     * administrators manage.
     * Application tenants partition the data of {@code MultiTenancyType.SHARED} entities owned by the application.
     */
    private String tenantId;

    @Override
    public ParticipantScope scope() {
        return new ParticipantScope(organizationId, applicationId, tenantId);
    }

    @Override
    public ParticipantIdentityType getType() {
        return ParticipantIdentityType.APPLICATION_USER;
    }
}
