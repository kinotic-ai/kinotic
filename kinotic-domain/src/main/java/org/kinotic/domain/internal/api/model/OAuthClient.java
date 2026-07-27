package org.kinotic.domain.internal.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;
import java.util.List;

/**
 * A dynamically registered OAuth 2.1 client (RFC 7591) allowed to request authorization codes
 * for the MCP endpoint. Every client is public ({@code token_endpoint_auth_method "none"}):
 * holding a client id grants nothing on its own, authority comes from the PKCE-bound
 * authorization-code flow approved by a signed-in user.
 * <p>
 * Internal-only — never published.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OAuthClient implements Identifiable<String> {

    /** The {@code client_id} presented on authorize and token requests. */
    private String id;

    /** Human-readable name shown on the consent page. */
    private String clientName;

    /** Exact-match allowlist for the {@code redirect_uri} on authorize requests. */
    private List<String> redirectUris;

    private Date created;
}
