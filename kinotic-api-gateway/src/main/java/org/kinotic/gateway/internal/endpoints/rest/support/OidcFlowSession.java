package org.kinotic.gateway.internal.endpoints.rest.support;

import java.io.Serializable;
import java.util.Map;

/**
 * Session state for one in-flight OIDC redirect flow.
 */
public record OidcFlowSession(String state,
                              String nonce,
                              String pkceVerifier,
                              String configId,
                              Map<String, String> extras) implements Serializable {

    public OidcFlowSession {
        extras = extras == null || extras.isEmpty() ? Map.of() : Map.copyOf(extras);
    }
}
