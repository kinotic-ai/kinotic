package org.kinotic.domain.api.rest.support;

import lombok.Getter;
import org.kinotic.domain.api.model.security.OidcProviderType;

/**
 * Signals a recoverable failure in the OIDC callback flow with a stable error code that
 * handlers can map directly into the URL fragment of the error redirect. The orchestrator
 * throws these so handlers can surface specific UX strings ({@code "state_mismatch"},
 * {@code "invalid_token"}, {@code "config_not_found"}) without parsing exception messages.
 */
@Getter
public class OidcCallbackException extends RuntimeException {

    private final String errorCode;
    /** The provider whose answer failed the flow, or {@code null} when the failure names none. */
    private final OidcProviderType provider;

    public OidcCallbackException(String errorCode) {
        this(errorCode, null);
    }

    public OidcCallbackException(String errorCode, OidcProviderType provider) {
        super(errorCode);
        this.errorCode = errorCode;
        this.provider = provider;
    }
}
