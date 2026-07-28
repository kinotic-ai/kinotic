package org.kinotic.domain.internal.api.model;

import java.util.List;

/**
 * A client's validated Client ID Metadata Document
 * (draft-ietf-oauth-client-id-metadata-document Section 4.1), resolved from the {@code client_id}
 * URL the client presented. Nothing here is stored: the client hosts its own metadata and the
 * authorization server re-fetches it on cache expiry.
 *
 * @param clientId     the document URL, which is also the client's identifier — its host is the
 *                     domain the client had to control to serve the document
 * @param clientName   human-readable name for the consent page, as claimed by the document
 * @param redirectUris the exact-match {@code redirect_uri} allowlist the document registers
 */
public record OAuthClientMetadata(String clientId,
                                  String clientName,
                                  List<String> redirectUris) {
}
