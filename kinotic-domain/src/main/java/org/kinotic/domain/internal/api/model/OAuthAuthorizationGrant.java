package org.kinotic.domain.internal.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * An OAuth 2.1 authorization-code flow in progress: created by the authorize endpoint, bound
 * to a user when the browser approves it on the consent page, and consumed (deleted) when the
 * client exchanges the code at the token endpoint.
 * <p>
 * Internal-only — never published. Only ever holds a SHA-256 hash of the authorization code,
 * never the plaintext.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OAuthAuthorizationGrant implements Identifiable<String> {

    /** Request id carried to the SPA consent page; not a secret, grants nothing by itself. */
    private String id;

    /** The client's Client ID Metadata Document URL, which is also its identifier. */
    private String clientId;

    /** Name from the client's metadata document, captured when the request was validated. */
    private String clientName;

    /** The exact {@code redirect_uri} from the authorize request; re-verified at code exchange. */
    private String redirectUri;

    /** PKCE S256 challenge from the authorize request. */
    private String codeChallenge;

    private String scope;

    /** RFC 8707 {@code resource} the client bound the request to, or {@code null}. */
    private String resource;

    /** Client-supplied CSRF value echoed back on the redirect, or {@code null}. */
    private String state;

    /**
     * Id of the {@link org.kinotic.domain.api.model.iam.IamUser} that approved the grant,
     * or {@code null} while the grant awaits consent.
     */
    private String userId;

    /** SHA-256 hash of the authorization code; {@code null} until the grant is approved. */
    private String codeHash;

    private Date created;

    /** Hard expiry after which the grant can no longer be approved or exchanged. */
    private Date expiresAt;
}
