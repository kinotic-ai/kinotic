package org.kinotic.gateway.internal.endpoints.rest.support;

import org.kinotic.domain.api.model.iam.BaseOidcConfiguration;

import java.util.Map;

/**
 * Outcome of {@link OidcFlowOrchestrator#handleCallback}: the configuration the IdP
 * round-trip ran against, the verified id_token claims, and the {@code orgId} and
 * {@code inviteToken} stashed on the flow session at start (flows that stashed neither
 * leave them {@code null}).
 */
public record CallbackResult<C extends BaseOidcConfiguration>(
        C config,
        Map<String, Object> claims,
        String orgId,
        String inviteToken) {
}
