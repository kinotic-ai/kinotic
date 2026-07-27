package org.kinotic.domain.api.model.iam;

import java.util.List;

/**
 * The result of a dynamic client registration (RFC 7591): the minted public client and the
 * values the registration response must echo.
 *
 * @param clientId          the minted {@code client_id}
 * @param clientName        the human-readable name shown on the consent page
 * @param redirectUris      the exact-match {@code redirect_uri} allowlist
 * @param clientIdIssuedAt  registration time, epoch seconds
 */
public record RegisteredOAuthClient(String clientId,
                                    String clientName,
                                    List<String> redirectUris,
                                    long clientIdIssuedAt) {
}
