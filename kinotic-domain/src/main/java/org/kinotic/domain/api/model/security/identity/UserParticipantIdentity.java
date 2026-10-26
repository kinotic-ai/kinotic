package org.kinotic.domain.api.model.security.identity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A person, at any scope: what every kind of user has, an email and sign-in methods of its own, a password and,
 * for an organization's or an application's user, any number of {@link OidcBinding}s. The concrete subtype is the
 * scope the person signs in to — {@link SystemParticipantIdentity}, {@link OrganizationParticipantIdentity}
 * or {@link ApplicationParticipantIdentity} — and one email may be a user of each, each with its own sign-in
 * methods.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public abstract sealed class UserParticipantIdentity extends ParticipantIdentity
        permits SystemParticipantIdentity, OrganizationParticipantIdentity, ApplicationParticipantIdentity {

    /** Email address, unique among the users of the same scope. */
    private String email;

    /**
     * Whether the user has yet to sign in for the first time: an administrator added it and no sign-in method signs
     * it in yet, so its invitation, or a first sign-in through one of its scope's providers under its email, is what
     * claims it. Cleared once the user has a password or a bound provider identity.
     */
    private boolean pending;

}
