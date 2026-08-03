package org.kinotic.domain.api.model.iam;

/**
 * Authentication method for an {@link ParticipantIdentity}.
 * LOCAL uses email/password with bcrypt-hashed credentials.
 * OIDC uses federated identity via an external provider (Google, Microsoft, etc.).
 * DELEGATED uses only Kinotic-issued tokens minted when a USER authorizes the delegate;
 * a DELEGATED identity has no login credential of its own.
 */
public enum AuthType {
    LOCAL,
    OIDC,
    DELEGATED
}
