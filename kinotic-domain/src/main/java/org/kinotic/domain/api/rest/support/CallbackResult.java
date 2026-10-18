package org.kinotic.domain.api.rest.support;

import org.kinotic.domain.api.model.security.OidcConfiguration;

import java.util.Map;

/**
 * Outcome of {@link OidcFlowOrchestrator#handleCallback}: the configuration the IdP
 * round-trip ran against, the verified id_token claims, the {@code orgId}, {@code applicationId},
 * {@code tenantId} and {@code inviteToken} stashed on the flow session at start (flows that stashed none
 * leave them {@code null}), and the {@code origin} of the page that started the flow.
 */
public record CallbackResult<C extends OidcConfiguration>(
        C config,
        Map<String, Object> claims,
        OidcFlowPurpose purpose,
        String orgId,
        String applicationId,
        String tenantId,
        String inviteToken,
        String accountId,
        String origin) {
}
