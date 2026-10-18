package org.kinotic.domain.api.model.security.identity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A person. One of two shapes, told apart by the scope fields:
 * <ul>
 *   <li>An <b>account</b>, with no {@code organizationId} or {@code applicationId}: the person's single
 *       identity on the platform, unique by email among accounts. Its sign-in methods are a password
 *       and any number of {@link OidcBinding}s, and the scopes it may sign in to are its
 *       {@link Membership}s, of organizations or of the platform. An account becomes a participant
 *       through one membership at a time.</li>
 *   <li>An <b>application user</b>, with {@code organizationId} and {@code applicationId} set: a user of
 *       one application's own user base, unique by email within that application, signing in with a
 *       password or an {@link OidcBinding} at the application's login.</li>
 * </ul>
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class UserParticipantIdentity extends ParticipantIdentity {

    /** Email address, unique among accounts, or within the application for an application's user. */
    private String email;

    @Override
    public ParticipantIdentityType getType() {
        return ParticipantIdentityType.USER;
    }

    /** Whether this is an account, a person's platform-wide identity, as opposed to an application's user. */
    @JsonIgnore
    public boolean isAccount() {
        return getOrganizationId() == null && getApplicationId() == null;
    }
}
