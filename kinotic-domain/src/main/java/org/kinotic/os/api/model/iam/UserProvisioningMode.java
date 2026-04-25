package org.kinotic.os.api.model.iam;

/**
 * Controls what happens when an OIDC callback produces a verified identity that has no
 * existing {@link IamUser} yet.
 */
public enum UserProvisioningMode {

    /**
     * Create an {@link IamUser} directly from the id_token claims on first login. Suitable
     * for consumer-style signups ("Continue with Google just works").
     */
    AUTO,

    /**
     * Stash the verified identity in a short-lived {@code PendingRegistration} and redirect
     * the user to a completion form so they can supply Kinotic-specific info (ToS, display
     * name, etc.) before the account is created.
     */
    REGISTRATION_REQUIRED,

    /**
     * Reserved: admins invite users via a time-scoped token (email + org metadata); OIDC
     * callbacks from non-invited identities are rejected. Not yet implemented — the
     * provisioning service rejects this mode until the invite-token flow is built.
     */
    INVITE_ONLY
}
