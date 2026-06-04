package org.kinotic.domain.api.model.iam;

/**
 * The provisioning policy an {@link OidcConfiguration} can declare for a verified identity that
 * has no existing {@link IamUser} — the intended handling for that provider's new identities.
 */
public enum UserProvisioningMode {

    /** Auto-create the {@link IamUser} from the id_token claims on first login (consumer-style). */
    AUTO,

    /** Require the user to complete a registration form before the account is created. */
    REGISTRATION_REQUIRED,

    /** Reserved: only pre-invited identities may register. */
    INVITE_ONLY
}
