package org.kinotic.billing.internal.endpoints;

import lombok.RequiredArgsConstructor;
import org.kinotic.billing.api.services.StripeWebhookEventService;
import org.springframework.stereotype.Component;

/**
 * Creates the billing module's verticles for deployment by {@link BillingInitializer}.
 */
@Component
@RequiredArgsConstructor
public class BillingVerticleFactory {

    private final StripeWebhookEventService webhookEventService;
    private final StripeEventDispatcher dispatcher;
    private final WebhookRetryPolicy retryPolicy;

    public StripeWebhookProcessor createStripeWebhookProcessor() {
        return new StripeWebhookProcessor(webhookEventService, dispatcher, retryPolicy);
    }
}
