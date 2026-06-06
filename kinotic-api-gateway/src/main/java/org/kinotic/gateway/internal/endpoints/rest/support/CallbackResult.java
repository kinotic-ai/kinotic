package org.kinotic.gateway.internal.endpoints.rest.support;

import org.kinotic.domain.api.model.iam.BaseOidcConfiguration;

import java.util.Map;

/**
 * Outcome of {@link OidcFlowOrchestrator#handleCallback}: the configuration the IdP
 * round-trip ran against, the verified id_token claims, and the {@code orgId} stashed on
 * the flow session at start (non-org-scoped flows leave it {@code null}).
 */
public record CallbackResult<C extends BaseOidcConfiguration>(
        C config,
        Map<String, Object> claims,
        String orgId) {
}
