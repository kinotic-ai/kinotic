package org.kinotic.billing.internal.endpoints;

import com.stripe.exception.SignatureVerificationException;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.billing.api.config.BillingConstants;
import org.kinotic.billing.api.config.KinoticBillingProperties;
import org.kinotic.billing.api.model.StripeWebhookEvent;
import org.kinotic.billing.api.model.StripeWebhookEventStatus;
import org.kinotic.billing.api.services.StripeWebhookEventService;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Receives Stripe webhook deliveries on the gateway router: verifies the signature,
 * persists the idempotency record, acknowledges, and hands processing to the
 * {@link StripeWebhookProcessor} via the local event bus.
 * <ul>
 *   <li>{@code POST /webhooks/stripe/platform} — events from Kinotic's own account</li>
 *   <li>{@code POST /webhooks/stripe/connected} — events from connected accounts</li>
 * </ul>
 * No session or user authentication applies — the signature is the authentication.
 * <p>
 * The gateway collects every {@link SuppliesGatewayRoutes} bean, so these routes mount only
 * when the billing module is enabled.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripeWebhookHandler implements SuppliesGatewayRoutes {

    private static final String SIGNATURE_HEADER = "Stripe-Signature";

    private final KinoticBillingProperties properties;
    private final StripeWebhookSignatureVerifier signatureVerifier;
    private final StripeWebhookEventService webhookEventService;
    private final Vertx vertx;

    @Override
    public void mountRoutes(Router router) {
        // Route-scoped body handler: these paths are outside /api/* on purpose, so neither
        // the gateway's global 16 KiB body cap nor its session handler applies here.
        router.post(BillingConstants.PLATFORM_WEBHOOK_PATH)
              .handler(BodyHandler.create(false).setBodyLimit(BillingConstants.WEBHOOK_BODY_LIMIT_BYTES))
              .handler(ctx -> handleWebhook(ctx, properties.getBilling().getPlatformWebhookSigningSecret()));
        router.post(BillingConstants.CONNECTED_WEBHOOK_PATH)
              .handler(BodyHandler.create(false).setBodyLimit(BillingConstants.WEBHOOK_BODY_LIMIT_BYTES))
              .handler(ctx -> handleWebhook(ctx, properties.getBilling().getConnectedWebhookSigningSecret()));
    }

    private void handleWebhook(RoutingContext ctx, String signingSecret) {
        Buffer body = ctx.body().buffer();
        if (body == null) {
            ctx.response().setStatusCode(400).end();
            return;
        }
        // The HMAC must cover the exact bytes Stripe signed — decode to String only once
        // and use it for both verification and persistence.
        String payload = body.toString(StandardCharsets.UTF_8);
        String signatureHeader = ctx.request().getHeader(SIGNATURE_HEADER);

        ParsedStripeEvent parsed;
        try {
            // HMAC over a ≤1 MiB body is microseconds — safe inline on the event loop.
            parsed = signatureVerifier.verifyAndParse(payload, signatureHeader, signingSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Rejecting Stripe webhook on {} — signature verification failed: {}",
                     ctx.request().path(), e.getMessage());
            ctx.response().setStatusCode(400).end();
            return;
        } catch (Exception e) {
            log.warn("Rejecting Stripe webhook on {} — unparseable payload: {}",
                     ctx.request().path(), e.getMessage());
            ctx.response().setStatusCode(400).end();
            return;
        }

        StripeWebhookEvent event = new StripeWebhookEvent()
                .setId(parsed.getId())
                .setEventType(parsed.getType())
                .setStripeAccountId(parsed.getStripeAccountId())
                .setLivemode(parsed.getLivemode())
                .setRelatedObjectId(parsed.getRelatedObjectId())
                .setPayload(payload)
                .setReceivedAt(new Date())
                .setStatus(StripeWebhookEventStatus.RECEIVED);

        webhookEventService.insertIfAbsent(event)
                           .whenComplete((inserted, error) -> {
                               if (error != null) {
                                   // Not acknowledged — Stripe will redeliver, which is what we
                                   // want if persistence is down.
                                   log.error("Failed to persist Stripe event {}", event.getId(), error);
                                   ctx.response().setStatusCode(500).end();
                                   return;
                               }
                               ctx.response().setStatusCode(200).end();
                               if (inserted) {
                                   vertx.eventBus().send(BillingConstants.STRIPE_WEBHOOK_EVENT_ADDRESS,
                                                         event.getId(),
                                                         new DeliveryOptions().setLocalOnly(true));
                               } else {
                                   log.debug("Duplicate Stripe event {} acknowledged without reprocessing",
                                             event.getId());
                               }
                           });
    }
}
