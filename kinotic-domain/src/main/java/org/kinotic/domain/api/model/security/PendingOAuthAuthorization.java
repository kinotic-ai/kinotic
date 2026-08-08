package org.kinotic.domain.api.model.security;

/**
 * What the consent page shows about an authorization request awaiting approval.
 *
 * @param clientName the name the client's metadata document claims
 * @param clientId   the client's Client ID Metadata Document URL; its host is the domain the client
 *                   had to control to serve that document, and is what the page identifies it by
 * @param scope      the requested scope, or {@code null} when the client requested none
 */
public record PendingOAuthAuthorization(String clientName,
                                        String clientId,
                                        String scope) {
}
