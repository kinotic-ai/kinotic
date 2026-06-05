package org.kinotic.gateway.internal.endpoints.rest.support;

/**
 * Session state for one in-flight OIDC redirect flow.
 */
public record OidcFlowSession(String state,
                              String nonce,
                              String pkceVerifier,
                              String configId,
                              String orgId) {
}
