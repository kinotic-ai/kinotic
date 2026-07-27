package org.kinotic.domain.api.model.iam;

/**
 * What the consent page shows about an authorization request awaiting approval.
 *
 * @param clientName the requesting client's registered display name
 * @param scope      the requested scope, or {@code null} when the client requested none
 */
public record PendingOAuthAuthorization(String clientName,
                                        String scope) {
}
