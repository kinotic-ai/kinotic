package org.kinotic.os.api.model.iam;

/**
 * Authentication method for an {@link IamUser}.
 * LOCAL uses email/password with bcrypt-hashed credentials.
 * OIDC uses federated identity via an external provider (Google, Microsoft, etc.).
 */
public enum AuthType {
    LOCAL,
    OIDC
}
