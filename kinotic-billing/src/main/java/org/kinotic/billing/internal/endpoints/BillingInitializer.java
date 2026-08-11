package org.kinotic.billing.internal.endpoints;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.kinotic.billing.api.services.StripeWebhookEventService;
import org.springframework.stereotype.Component;

/**
 * Deploys the billing verticles at startup. The webhook HTTP routes are mounted on the
 * api-gateway router (see {@link StripeWebhookHandler}), so only the processor deploys here.
 */
@Component
@RequiredArgsConstructor
public class BillingInitializer {

    private final StripeWebhookEventService webhookEventService;
    private final StripeEventDispatcher dispatcher;
    private final WebhookRetryPolicy retryPolicy;
    private final Vertx vertx;

    @PostConstruct
    public void init() {
        // One instance: processing is webhook-rate work, and a single consumer keeps the
        // in-memory retry state simple. Raise instances if event volume ever demands it.
        vertx.deployVerticle(() -> new StripeWebhookProcessor(webhookEventService, dispatcher, retryPolicy),
                             new DeploymentOptions());
    }
}
