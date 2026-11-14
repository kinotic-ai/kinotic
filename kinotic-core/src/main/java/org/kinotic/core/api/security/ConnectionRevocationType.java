package org.kinotic.core.api.security;

/**
 * What the id of a {@link ConnectionRevocation} names.
 */
public enum ConnectionRevocationType {
    /** An identity: every connection authenticated as it ends. */
    IDENTITY,
    /** A browser login: every connection established on it ends. */
    LOGIN
}
