/**
 * Authentication method for an {@link ParticipantIdentity}.
 */
export enum AuthType {
    /** Email/password with bcrypt-hashed credentials. */
    LOCAL = 'LOCAL',
    /** Federated identity via an external OIDC provider (Google, Microsoft, etc.). */
    OIDC = 'OIDC',
    /**
     * Only Kinotic-issued tokens minted when a USER authorizes the delegate; a DELEGATED
     * identity has no login credential of its own.
     */
    DELEGATED = 'DELEGATED',
    /**
     * A provisioned client id and secret presented as connection credentials; the
     * authentication method of every MACHINE identity.
     */
    CLIENT_CREDENTIALS = 'CLIENT_CREDENTIALS'
}
