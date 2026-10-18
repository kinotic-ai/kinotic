package org.kinotic.domain.api.rest.support;

/**
 * What a provider round-trip is for. Every flow through one configuration returns to the one callback the
 * configuration is registered with at its provider, so the callback reads what to do from the purpose the
 * start recorded on the flow session rather than from the path the provider returned to.
 */
public enum OidcFlowPurpose {
    /** Signs the identity's user in to the scope the flow names. */
    LOGIN,
    /** Verifies the identity a new organization's founder signs up with. */
    SIGN_UP,
    /** Accepts the invitation whose token the flow carries, as the identity. */
    INVITATION,
    /** Binds the identity to the account the flow carries, as a further sign-in method. */
    CONNECT
}
