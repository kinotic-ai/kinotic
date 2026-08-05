package org.kinotic.domain.api.model.iam;

/**
 * Authentication method for an {@link ParticipantIdentity}.
 * LOCAL uses email/password with bcrypt-hashed credentials.
 * OIDC uses federated identity via an external provider (Google, Microsoft, etc.).
 * DELEGATED uses only Kinotic-issued tokens minted when a USER authorizes the delegate;
 * a DELEGATED identity has no login credential of its own.
 * CLIENT_CREDENTIALS uses the RFC 6749 client-credentials grant with a provisioned secret;
 * the authentication method of every MACHINE identity.
 */
public enum AuthType {
    LOCAL,
    OIDC,
    DELEGATED,
    CLIENT_CREDENTIALS
}
