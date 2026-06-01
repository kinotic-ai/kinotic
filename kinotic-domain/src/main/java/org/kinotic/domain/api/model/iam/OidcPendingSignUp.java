package org.kinotic.domain.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * A {@link PendingSignUp} for the federated (OIDC) flow. The verified identity is produced by
 * the IdP callback and parked here while the user names the Organization to create (or, in the
 * {@link UserProvisioningMode#REGISTRATION_REQUIRED} path, joins an existing one).
 */
@Getter
@Setter
@NoArgsConstructor
public final class OidcPendingSignUp extends PendingSignUp {

    // ── Verified OIDC identity (never fill these from user input — must come from id_token) ──

    /** OIDC {@code sub} claim — stable identifier within the issuer. */
    private String oidcSubject;

    /** Reference to the {@link OidcConfiguration} that produced the identity. */
    private String oidcConfigId;

    /**
     * Organization the created {@link IamUser} will belong to. May be {@code null} while the
     * registration is pending — the social-signup path doesn't learn the org until the user
     * names it at completion.
     */
    private String organizationId;

    /** Additional OIDC claims preserved for the registration form and eventual user record. */
    private Map<String, Object> additionalClaims;
}
