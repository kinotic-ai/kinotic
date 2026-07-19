package org.kinotic.billing.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.kinotic.billing.api.model.StripeWebhookEvent;
import org.kinotic.billing.api.model.StripeWebhookEventStatus;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Component
public class StripeWebhookEventRepository extends AbstractRepository<StripeWebhookEvent> {

    public StripeWebhookEventRepository(ElasticsearchAsyncClient esAsyncClient,
                                        CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_stripe_webhook_event", StripeWebhookEvent.class, esAsyncClient, crudServiceTemplate);
    }

    /**
     * Persists the event only if no document with its id exists yet, atomically.
     *
     * @return {@code true} if the event was inserted, {@code false} if an event with the
     *         same id already exists (a duplicate webhook delivery)
     */
    /**
     * Returns events whose processing never completed ({@code RECEIVED} or {@code FAILED});
     * dead-lettered events are excluded.
     */
    public CompletableFuture<Page<StripeWebhookEvent>> findUnprocessed(Pageable pageable) {
        return findAll(pageable, b -> b.query(Query.of(q -> q.bool(bq -> bq
                .minimumShouldMatch("1")
                .should(termFilter("status", StripeWebhookEventStatus.RECEIVED.name()))
                .should(termFilter("status", StripeWebhookEventStatus.FAILED.name()))))));
    }

    public CompletableFuture<Boolean> insertIfAbsent(StripeWebhookEvent event) {
        // ES create op-type is the atomic insert-or-409; a find-then-save would race with
        // Stripe's concurrent redeliveries.
        return save(event, b -> b.opType(OpType.Create))
                .handle((saved, error) -> {
                    if (error == null) {
                        return true;
                    }
                    Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                    if (cause instanceof ElasticsearchException ee && ee.status() == 409) {
                        return false;
                    }
                    throw new CompletionException(cause);
                });
    }
}
