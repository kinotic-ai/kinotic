package org.kinotic.core.api.security;

/**
 * What a {@link ConnectionRevocationService} revocation names: every connection authenticated as an identity, or
 * every connection a browser login established.
 */
public enum ConnectionRevocationType {
    IDENTITY,
    LOGIN
}
