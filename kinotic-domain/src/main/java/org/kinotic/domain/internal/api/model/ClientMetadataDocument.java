package org.kinotic.domain.internal.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * The wire form of a client metadata document, as fetched from the Client ID Metadata Document URL
 * the client presented as its {@code client_id}. Every value here is what the client claims about
 * itself; {@code ClientMetadataDocumentService} validates the document before any of it is acted on.
 * <p>
 * Properties other than the ones mapped here are ignored.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientMetadataDocument {

    /** Must equal the URL the document was served from. */
    @JsonProperty("client_id")
    private String clientId;

    /** Human-readable name for the consent page. */
    @JsonProperty("client_name")
    private String clientName;

    /** The exact-match {@code redirect_uri} allowlist this document registers. */
    @JsonProperty("redirect_uris")
    private List<String> redirectUris;

    /** The token endpoint authentication method the client prefers. */
    @JsonProperty("token_endpoint_auth_method")
    private String tokenEndpointAuthMethod;

    /** Every token endpoint authentication method the client can use, when it can use more than one. */
    @JsonProperty("token_endpoint_auth_methods_supported")
    private List<String> tokenEndpointAuthMethodsSupported;

    /** Read only so a document that carries a secret can be rejected. */
    @JsonProperty("client_secret")
    private String clientSecret;

    /** Read only so a document that carries a secret can be rejected. */
    @JsonProperty("client_secret_expires_at")
    private Long clientSecretExpiresAt;
}
