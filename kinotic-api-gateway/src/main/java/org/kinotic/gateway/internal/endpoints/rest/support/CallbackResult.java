package org.kinotic.gateway.internal.endpoints.rest.support;

import org.kinotic.domain.api.model.iam.BaseOidcConfiguration;

import java.util.Map;

/**
 * Outcome of {@link OidcFlowOrchestrator#handleCallback}: the configuration the IdP
 * round-trip ran against, the verified id_token claims, the {@code orgId} stashed on
 * the flow session at start, and the {@code inviteToken} stashed for an invite-acceptance
 * flow (non-org-scoped and non-invite flows leave the respective field {@code null}).
 */
public record CallbackResult<C extends BaseOidcConfiguration>(
        C config,
        Map<String, Object> claims,
        String orgId,
        String inviteToken) {
}
