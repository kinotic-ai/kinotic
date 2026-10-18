package org.kinotic.managementserver.internal.api.rest;

import io.vertx.ext.web.RoutingContext;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.rest.support.CallbackResult;
import org.kinotic.domain.api.rest.support.OidcFlowPurpose;
import org.kinotic.domain.api.rest.support.PendingFlow;

/**
 * Finishes the flows of one {@link OidcFlowPurpose} once {@link OrganizationOidcCallbackHandler} has validated
 * the provider's callback: the handler that starts a purpose's flows is the one that completes them.
 */
interface OidcFlowCompleter {

    OidcFlowPurpose purpose();

    /** Finishes a validated callback: the identity's claims are verified and the flow's own fields are at hand. */
    void complete(RoutingContext ctx, CallbackResult<OidcConfiguration> result);

    /**
     * Sends the browser on after a callback failed validation, to wherever a failure of this purpose is shown;
     * {@code flow} is what was known of the flow before it was consumed.
     */
    void fail(RoutingContext ctx, PendingFlow flow, Throwable failure);
}
