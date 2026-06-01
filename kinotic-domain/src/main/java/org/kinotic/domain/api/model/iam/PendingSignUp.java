package org.kinotic.domain.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * A short-lived, single-use record capturing a sign-up in flight — held from the moment an
 * identity is established until the user completes onboarding, at which point it is consumed
 * to create an Organization and its first admin {@link IamUser}, then deleted.
 * <p>
 * The subtype encodes <em>how the email was established</em>, which is the only axis on which
 * the two sign-up flows genuinely differ:
 * <ul>
 *   <li>{@link LocalPendingSignUp} — email/password sign-up. The email is unverified until the
 *       user clicks the link carrying {@link #getVerificationToken()}; that click <em>is</em>
 *       the verification, and a password is chosen only at completion.</li>
 *   <li>{@link OidcPendingSignUp} — federated sign-up. The IdP already verified the email; the
 *       token is merely a handoff parking the verified identity until the user names their org.
 *       No password is ever associated with an OIDC identity.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
public sealed abstract class PendingSignUp implements Identifiable<String>
        permits LocalPendingSignUp, OidcPendingSignUp {

    private String id;

    /** Opaque single-use token carried in the completion URL. */
    private String verificationToken;

    /** Hard expiry after which the token is invalid. */
    private Date expiresAt;

    private Date created;

    /** Email the eventual {@link IamUser} will be created with. */
    private String email;

    /** Suggested display name for the eventual {@link IamUser}. */
    private String displayName;
}
