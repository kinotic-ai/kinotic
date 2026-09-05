package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;

import java.util.Date;

/**
 * A short-lived, single-use record capturing a sign-up in flight — held from the moment an
 * identity is established until the user completes onboarding, at which point it is consumed to
 * create the new Organization and its admin {@link ParticipantIdentity}, then deleted.
 * <p>
 * {@link #authType} records how the email was established: LOCAL (email/password verified by
 * clicking the link) or OIDC (federated, pre-verified by the IdP, carrying {@link #oidcSubject}
 * and {@link #oidcConfigId}).
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PendingSignUp implements PendingVerification {

    private String id;

    /** Opaque single-use token carried in the completion URL. */
    private String verificationToken;

    /** Hard expiry after which the token is invalid. */
    private Date expiresAt;

    private Date created;

    /** Email the eventual {@link ParticipantIdentity} is created with. */
    private String email;

    /** Display name for the eventual {@link ParticipantIdentity}. For OIDC this is taken from the IdP claims. */
    private String displayName;

    /** How the email was established — drives whether a password/credential is involved. */
    private AuthType authType;

    /** OIDC {@code sub} claim; null for LOCAL. */
    private String oidcSubject;

    /** Id of the OIDC configuration that produced the identity; null for LOCAL. */
    private String oidcConfigId;
}
