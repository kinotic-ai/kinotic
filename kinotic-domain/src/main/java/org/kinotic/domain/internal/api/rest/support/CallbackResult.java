package org.kinotic.domain.internal.api.rest.support;

import org.kinotic.domain.api.model.security.BaseOidcConfiguration;

import java.util.Map;

/**
 * Outcome of {@link OidcFlowOrchestrator#handleCallback}: the configuration the IdP
 * round-trip ran against, the verified id_token claims, the {@code orgId} and
 * {@code inviteToken} stashed on the flow session at start (flows that stashed neither
 * leave them {@code null}), and the {@code origin} of the page that started the flow.
 */
public record CallbackResult<C extends BaseOidcConfiguration>(
        C config,
        Map<String, Object> claims,
        String orgId,
        String inviteToken,
        String origin) {
}
