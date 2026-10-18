package org.kinotic.domain.api.rest.support;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.security.OidcConfiguration;

/**
 * Loads the configuration an OIDC callback completes against, scoped by what the flow stashed at its start:
 * the organization, the application and the tenant the sign-in is to, each {@code null} when the flow
 * stashed none. Resolves to {@code null} when the scope does not offer the configuration.
 *
 * @param <C> the kind of configuration the route completes against
 */
@FunctionalInterface
public interface FlowConfigResolver<C extends OidcConfiguration> {

    Future<C> resolve(String orgId, String applicationId, String tenantId);
}
