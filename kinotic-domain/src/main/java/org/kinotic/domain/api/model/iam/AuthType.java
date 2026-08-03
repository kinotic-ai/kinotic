package org.kinotic.domain.api.model.iam;

/**
 * Authentication method for an {@link ParticipantIdentity}.
 * LOCAL uses email/password with bcrypt-hashed credentials.
 * OIDC uses federated identity via an external provider (Google, Microsoft, etc.).
 */
public enum AuthType {
    LOCAL,
    OIDC
}
