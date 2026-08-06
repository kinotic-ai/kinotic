package org.kinotic.domain.api.model.security;

/**
 * Authentication method for an {@link ParticipantIdentity}.
 */
public enum AuthType {

    /** Email/password with bcrypt-hashed credentials. */
    LOCAL,

    /** Federated identity via an external OIDC provider (Google, Microsoft, etc.). */
    OIDC,

    /**
     * Only Kinotic-issued tokens minted when a USER authorizes the delegate; a DELEGATED
     * identity has no login credential of its own.
     */
    DELEGATED,

    /**
     * The RFC 6749 client-credentials grant with a provisioned secret; the authentication
     * method of every MACHINE identity.
     */
    CLIENT_CREDENTIALS
}
