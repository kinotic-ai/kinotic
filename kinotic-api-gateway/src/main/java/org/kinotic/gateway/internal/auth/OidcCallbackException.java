package org.kinotic.gateway.internal.auth;

import lombok.Getter;

/**
 * Signals a recoverable failure in the OIDC callback flow with a stable error code that
 * handlers can map directly into the URL fragment of the error redirect. The orchestrator
 * throws these so handlers can surface specific UX strings ({@code "state_mismatch"},
 * {@code "invalid_token"}, {@code "config_not_found"}) without parsing exception messages.
 */
@Getter
public class OidcCallbackException extends RuntimeException {

    private final String errorCode;

    public OidcCallbackException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }
}
