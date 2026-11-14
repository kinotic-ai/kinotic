package org.kinotic.system.internal.api.services.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.service.SuppliesGatewayRoutes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code POST /api/billing/stripe/webhook}: Stripe's webhook endpoint for billing organizations. Every delivery is
 * verified against the endpoint's signing secret and answered with a failure when handling fails, so that Stripe
 * redelivers it.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.billing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class StripeWebhookRoutes implements SuppliesGatewayRoutes {

    /** The route Stripe delivers to, registered on the endpoint in the Stripe Dashboard. */
    public static final String PATH = "/api/billing/stripe/webhook";

    private static final String SIGNATURE_HEADER = "Stripe-Signature";
    // an event with its object runs well past the gateway's default body limit
    private static final long MAX_EVENT_BYTES = 1024 * 1024;

    private final DefaultBillingOperationsService operationsService;
    private final StripeBilling stripeBilling;

    @Override
    public void mountRoutes(Router router) {
        // ahead of the gateway's own /api/* handlers, so this route reads the body with its own limit and Stripe's
        // server-to-server call creates no browser session
        router.post(PATH)
              .order(-1)
              .handler(BodyHandler.create(false).setBodyLimit(MAX_EVENT_BYTES))
              .handler(this::deliver);
    }

    private void deliver(RoutingContext ctx) {
        String payload = ctx.body().asString();
        String signature = ctx.request().getHeader(SIGNATURE_HEADER);
        if (payload == null || signature == null) {
            ctx.response().setStatusCode(400).end();
            return;
        }
        Event event;
        try {
            event = stripeBilling.verifyEvent(payload, signature);
        } catch (SignatureVerificationException e) {
            log.warn("A Stripe webhook delivery failed signature verification: {}", e.getMessage());
            ctx.response().setStatusCode(400).end();
            return;
        }
        // composed from a succeeded future so a synchronous throw, as an unreadable event raises, fails like any other
        Future.succeededFuture()
              .compose(v -> operationsService.handleEvent(event))
              .onSuccess(v -> ctx.response().setStatusCode(200).end())
              .onFailure(t -> {
                  log.error("Handling the Stripe event {} ({}) failed", event.getId(), event.getType(), t);
                  ctx.response().setStatusCode(500).end();
              });
    }
}
