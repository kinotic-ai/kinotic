package org.kinotic.domain.internal.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * The wire form of a Client ID Metadata Document
 * (draft-ietf-oauth-client-id-metadata-document Section 4.1), as fetched from the client's
 * {@code client_id} URL. Every value here is what the client claims about itself;
 * {@code OAuthClientResolver} validates the document before any of it is acted on.
 * <p>
 * Section 4.1 permits a document to carry additional properties, so unknown fields are ignored
 * rather than treated as an error.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientIdMetadataDocument {

    /** Must equal the URL the document was served from. */
    @JsonProperty("client_id")
    private String clientId;

    /** Human-readable name for the consent page. */
    @JsonProperty("client_name")
    private String clientName;

    /** The exact-match {@code redirect_uri} allowlist this document registers. */
    @JsonProperty("redirect_uris")
    private List<String> redirectUris;

    /** Must be {@code none} when present — a document-identified client can hold no shared secret. */
    @JsonProperty("token_endpoint_auth_method")
    private String tokenEndpointAuthMethod;

    /** Read only so a document that carries a secret can be rejected. */
    @JsonProperty("client_secret")
    private String clientSecret;

    /** Read only so a document that carries a secret can be rejected. */
    @JsonProperty("client_secret_expires_at")
    private Long clientSecretExpiresAt;
}
