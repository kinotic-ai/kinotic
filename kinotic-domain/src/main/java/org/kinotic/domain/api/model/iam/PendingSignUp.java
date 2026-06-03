package org.kinotic.domain.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;
import java.util.Map;

/**
 * A short-lived, single-use record capturing a sign-up in flight — held from the moment an
 * identity is established until the user completes onboarding, at which point it is consumed to
 * create the {@link IamUser} (and, for a brand-new organization, the Organization itself), then
 * deleted.
 * <p>
 * One flat shape covers every sign-up. {@link #authType} records how the email was established
 * (LOCAL = email/password verified by clicking the link; OIDC = federated, pre-verified by the
 * IdP), and the scope fields record where the resulting user lands:
 * <ul>
 *   <li>{@code organizationId} and {@code applicationId} both null — a new organization is
 *       created at completion (the founder names it then);</li>
 *   <li>{@code organizationId} set, {@code applicationId} null — the user joins that existing
 *       organization;</li>
 *   <li>{@code organizationId} and {@code applicationId} set — the user is an end-user of that
 *       application, under {@code tenantId}.</li>
 * </ul>
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PendingSignUp implements Identifiable<String> {

    private String id;

    /** Opaque single-use token carried in the completion URL. */
    private String verificationToken;

    /** Hard expiry after which the token is invalid. */
    private Date expiresAt;

    private Date created;

    /** Email the eventual {@link IamUser} is created with. */
    private String email;

    /** Display name for the eventual {@link IamUser}. For OIDC this is taken from the IdP claims. */
    private String displayName;

    /** How the email was established — drives whether a password/credential is involved. */
    private AuthType authType;

    /** OIDC {@code sub} claim; null for LOCAL. */
    private String oidcSubject;

    /** Id of the OIDC configuration that produced the identity; null for LOCAL. */
    private String oidcConfigId;

    /** Additional OIDC claims preserved for the completion form and eventual user record. */
    private Map<String, Object> additionalClaims;

    /**
     * Organization the resulting user lands in, or null when completion creates a new org. Set up
     * front for the existing-org and application flows.
     */
    private String organizationId;

    /** Application the resulting user is an end-user of; null for organization sign-ups. */
    private String applicationId;

    /** Tenant slice the application end-user belongs to; null outside application sign-ups. */
    private String tenantId;
}
