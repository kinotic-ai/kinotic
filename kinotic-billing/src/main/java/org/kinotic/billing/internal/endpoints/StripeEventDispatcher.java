package org.kinotic.billing.internal.endpoints;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.billing.api.model.StripeWebhookEvent;
import org.kinotic.billing.api.services.RevenueSplitService;

import java.util.concurrent.CompletableFuture;

/**
 * Routes a persisted webhook event to the service that owns its event type. Event types
 * without a handler complete successfully — Stripe sends many types we don't subscribe
 * logic to yet, and ignoring them must not trigger retries.
 */
@Slf4j
@RequiredArgsConstructor
public class StripeEventDispatcher {

    private final RevenueSplitService revenueSplitService;

    /**
     * Whether this deployment expects live events, derived from the configured API key's
     * mode. Stripe sends test events ({@code livemode=false}) to live URLs; they are
     * acknowledged but never processed.
     */
    private final boolean expectedLivemode;

    public CompletableFuture<Void> dispatch(StripeWebhookEvent event) {
        if (event.getEventType() == null) {
            // A verified Stripe payload always carries a type; its absence means the record
            // is corrupt — fail (→ dead-letter) rather than NPE in the switch below.
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Stripe event " + event.getId() + " carries no event type"));
        }
        if (event.getLivemode() != null && event.getLivemode() != expectedLivemode) {
            log.warn("Ignoring Stripe event {} ({}): livemode={} but this deployment expects livemode={}",
                     event.getId(), event.getEventType(), event.getLivemode(), expectedLivemode);
            return CompletableFuture.completedFuture(null);
        }
        return switch (event.getEventType()) {
            case "charge.succeeded" -> {
                if (event.getRelatedObjectId() == null) {
                    yield CompletableFuture.failedFuture(new IllegalStateException(
                            "charge.succeeded event " + event.getId() + " carries no charge id"));
                }
                yield revenueSplitService.recordCharge(event.getRelatedObjectId())
                                         .thenApply(split -> null);
            }
            default -> {
                log.debug("No handler for Stripe event type {} ({}); acknowledged without processing",
                          event.getEventType(), event.getId());
                yield CompletableFuture.completedFuture(null);
            }
        };
    }
}
