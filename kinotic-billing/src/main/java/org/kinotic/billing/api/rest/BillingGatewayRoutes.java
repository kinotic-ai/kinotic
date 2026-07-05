package org.kinotic.billing.api.rest;

import io.vertx.ext.web.Router;
import lombok.RequiredArgsConstructor;
import org.kinotic.billing.internal.endpoints.StripeWebhookHandler;
import org.springframework.stereotype.Component;

/**
 * Wires billing REST routes onto the gateway router.
 * <ul>
 *   <li>{@code POST /webhooks/stripe/platform} — Stripe platform-stream deliveries</li>
 *   <li>{@code POST /webhooks/stripe/connected} — Stripe connected-accounts-stream deliveries</li>
 * </ul>
 * This bean only exists when the billing module is enabled; the gateway mounts it via an
 * {@code ObjectProvider} so it can be absent.
 */
@Component
@RequiredArgsConstructor
public class BillingGatewayRoutes {

    private final StripeWebhookHandler stripeWebhookHandler;

    public void mountRoutes(Router router) {
        stripeWebhookHandler.mountRoutes(router);
    }
}
