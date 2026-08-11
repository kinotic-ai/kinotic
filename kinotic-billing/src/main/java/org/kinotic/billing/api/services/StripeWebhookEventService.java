package org.kinotic.billing.api.services;

import org.kinotic.billing.api.model.StripeWebhookEvent;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;

import java.util.concurrent.CompletableFuture;

/**
 * Persistence for Stripe webhook idempotency records and their processing state.
 */
public interface StripeWebhookEventService extends IdentifiableCrudService<StripeWebhookEvent, String> {

    /**
     * Persists the event only if it hasn't been seen before, atomically.
     *
     * @return {@code true} if the event is new, {@code false} for a duplicate delivery
     */
    CompletableFuture<Boolean> insertIfAbsent(StripeWebhookEvent event);

    /**
     * Returns events whose processing never completed ({@code RECEIVED} or {@code FAILED});
     * dead-lettered events are excluded.
     */
    CompletableFuture<Page<StripeWebhookEvent>> findUnprocessed(Pageable pageable);

    CompletableFuture<Void> markProcessed(String eventId);

    CompletableFuture<Void> markFailed(String eventId, String lastError);

    CompletableFuture<Void> markDeadLettered(String eventId, String lastError);
}
