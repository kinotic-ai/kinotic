package org.kinotic.gateway.internal.auth;

import org.kinotic.os.api.model.iam.BaseOidcConfiguration;

import java.util.Map;

/**
 * Outcome of {@link OidcFlowOrchestrator#handleCallback}: the configuration the IdP
 * round-trip ran against, the verified id_token claims, and any extra session values
 * the handler stashed on start (e.g. {@code orgId} for the SSO path).
 */
public record CallbackResult<C extends BaseOidcConfiguration>(
        C config,
        Map<String, Object> claims,
        Map<String, String> extras) {
}
