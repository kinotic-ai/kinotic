package org.kinotic.os.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;
import java.util.Map;

/**
 * Holds a verified OIDC identity between the callback and the user completing a
 * {@link UserProvisioningMode#REGISTRATION_REQUIRED} form. Short-lived (minutes, not days);
 * consumed once to create a real {@link IamUser}. Never contains a password — OIDC identities
 * never do.
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>OIDC callback produces a verified id_token and no matching {@link IamUser} exists.</li>
 *   <li>Provisioning service writes a {@code PendingRegistration} with the verified subject,
 *       config id, email, and any extra claims worth preserving.</li>
 *   <li>Backend redirects the browser to a completion URL with the {@link #verificationToken}
 *       so the frontend can render a pre-filled registration form.</li>
 *   <li>User submits → service finds by token, creates the {@link IamUser} in the target
 *       scope, deletes the pending record.</li>
 * </ol>
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PendingRegistration implements Identifiable<String> {

    private String id;

    /** Opaque single-use token carried in the completion URL. */
    private String verificationToken;

    /** Hard expiry after which the token is invalid. Typically 10 minutes. */
    private Date expiresAt;

    private Date created;

    // ── Verified OIDC identity (never fill these from user input — must come from id_token) ──

    /** OIDC {@code sub} claim — stable identifier within the issuer. */
    private String oidcSubject;

    /** Reference to the {@link OidcConfiguration} that produced the identity. */
    private String oidcConfigId;

    /** Email from the id_token; guaranteed verified (we reject {@code email_verified=false}). */
    private String email;

    /** Suggested display name from the id_token ({@code name}, {@code preferred_username}, etc.). */
    private String displayName;

    // ── Target scope for the eventual IamUser ──

    /** Scope layer for the user to be created ({@code SYSTEM}, {@code ORGANIZATION}, {@code APPLICATION}). */
    private String authScopeType;

    /** Scope identifier for the user to be created. */
    private String authScopeId;

    /** Additional OIDC claims preserved for the registration form and eventual user record. */
    private Map<String, Object> additionalClaims;
}
