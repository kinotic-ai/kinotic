package org.kinotic.billing.api.rest;

import io.vertx.ext.web.Router;
import lombok.RequiredArgsConstructor;
import org.kinotic.billing.internal.endpoints.StripeWebhookHandler;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.springframework.stereotype.Component;

/**
 * Wires billing REST routes onto the gateway router.
 * <ul>
 *   <li>{@code POST /webhooks/stripe/platform} — Stripe platform-stream deliveries</li>
 *   <li>{@code POST /webhooks/stripe/connected} — Stripe connected-accounts-stream deliveries</li>
 * </ul>
 * This bean only exists when the billing module is enabled; the gateway collects every
 * {@link SuppliesGatewayRoutes} bean, so a disabled module simply contributes no routes.
 */
@Component
@RequiredArgsConstructor
public class BillingGatewayRoutes implements SuppliesGatewayRoutes {

    private final StripeWebhookHandler stripeWebhookHandler;

    @Override
    public void mountRoutes(Router router) {
        stripeWebhookHandler.mountRoutes(router);
    }
}
