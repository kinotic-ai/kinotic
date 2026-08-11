package org.kinotic.billing.internal.endpoints;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.billing.api.config.BillingConstants;
import org.kinotic.billing.api.model.StripeWebhookEvent;
import org.kinotic.billing.api.services.StripeWebhookEventService;
import org.kinotic.core.api.crud.Pageable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Consumes persisted webhook event ids from the local event bus and drives them through
 * the {@link StripeEventDispatcher}, with bounded exponential-backoff retries. Exhausted
 * events are dead-lettered for human attention.
 * <p>
 * Startup re-drives events left RECEIVED or FAILED by a previous crash — Stripe's own
 * redelivery does not cover them because the delivery was already acknowledged.
 */
@Slf4j
@RequiredArgsConstructor
public class StripeWebhookProcessor extends VerticleBase {

    private final StripeWebhookEventService webhookEventService;
    private final StripeEventDispatcher dispatcher;
    private final WebhookRetryPolicy retryPolicy;

    @Override
    public Future<?> start() {
        vertx.eventBus().<String>consumer(BillingConstants.STRIPE_WEBHOOK_EVENT_ADDRESS,
                                          message -> process(message.body(), 1));
        redriveUnprocessed();
        return Future.succeededFuture();
    }

    private void redriveUnprocessed() {
        // Processing is idempotent (deterministic ids, existence checks), so a concurrent
        // node sweeping the same events converges. One batch per boot; anything beyond it
        // is picked up on the next restart.
        webhookEventService.findUnprocessed(Pageable.ofSize(500))
                           .whenComplete((page, error) -> {
                               if (error != null) {
                                   log.error("Failed to query unprocessed Stripe webhook events", error);
                                   return;
                               }
                               if (!page.getContent().isEmpty()) {
                                   log.info("Re-driving {} unprocessed Stripe webhook events",
                                            page.getContent().size());
                                   for (StripeWebhookEvent event : page.getContent()) {
                                       process(event.getId(), 1);
                                   }
                               }
                           });
    }

    private void process(String eventId, int attempt) {
        webhookEventService.findById(eventId)
                           .thenCompose(event -> {
                               if (event == null) {
                                   return CompletableFuture.failedFuture(new IllegalStateException(
                                           "No StripeWebhookEvent found for id " + eventId));
                               }
                               return dispatcher.dispatch(event);
                           })
                           .whenComplete((v, error) -> {
                               if (error == null) {
                                   markSafely(webhookEventService.markProcessed(eventId), eventId);
                                   return;
                               }
                               String errorMessage = rootMessage(error);
                               if (retryPolicy.isExhausted(attempt)) {
                                   log.error("Stripe event {} dead-lettered after {} attempts: {}",
                                             eventId, attempt, errorMessage);
                                   markSafely(webhookEventService.markDeadLettered(eventId, errorMessage), eventId);
                               } else {
                                   log.warn("Stripe event {} failed attempt {}/{} — retrying: {}",
                                            eventId, attempt, WebhookRetryPolicy.MAX_ATTEMPTS, errorMessage);
                                   markSafely(webhookEventService.markFailed(eventId, errorMessage), eventId);
                                   vertx.setTimer(retryPolicy.delayMillis(attempt),
                                                  timerId -> process(eventId, attempt + 1));
                               }
                           });
    }

    private void markSafely(CompletableFuture<Void> statusUpdate, String eventId) {
        statusUpdate.whenComplete((v, error) -> {
            if (error != null) {
                log.error("Failed to update status of Stripe event {}", eventId, error);
            }
        });
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause() : error;
        return cause.toString();
    }
}
