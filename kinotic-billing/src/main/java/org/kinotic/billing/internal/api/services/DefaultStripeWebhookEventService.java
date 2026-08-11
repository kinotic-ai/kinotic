package org.kinotic.billing.internal.api.services;

import org.kinotic.billing.api.model.StripeWebhookEvent;
import org.kinotic.billing.api.model.StripeWebhookEventStatus;
import org.kinotic.billing.api.services.StripeWebhookEventService;
import org.kinotic.billing.internal.api.repositories.StripeWebhookEventRepository;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultStripeWebhookEventService extends AbstractCrudService<StripeWebhookEvent>
        implements StripeWebhookEventService {

    private final StripeWebhookEventRepository webhookEventRepository;

    public DefaultStripeWebhookEventService(StripeWebhookEventRepository repository) {
        super(repository);
        this.webhookEventRepository = repository;
    }

    @Override
    public CompletableFuture<Boolean> insertIfAbsent(StripeWebhookEvent event) {
        return webhookEventRepository.insertIfAbsent(event);
    }

    @Override
    public CompletableFuture<Page<StripeWebhookEvent>> findUnprocessed(Pageable pageable) {
        return webhookEventRepository.findUnprocessed(pageable);
    }

    @Override
    public CompletableFuture<Void> markProcessed(String eventId) {
        return updateStatus(eventId, StripeWebhookEventStatus.PROCESSED, null);
    }

    @Override
    public CompletableFuture<Void> markFailed(String eventId, String lastError) {
        return updateStatus(eventId, StripeWebhookEventStatus.FAILED, lastError);
    }

    @Override
    public CompletableFuture<Void> markDeadLettered(String eventId, String lastError) {
        return updateStatus(eventId, StripeWebhookEventStatus.DEAD_LETTERED, lastError);
    }

    private CompletableFuture<Void> updateStatus(String eventId,
                                                 StripeWebhookEventStatus status,
                                                 String lastError) {
        return webhookEventRepository.findById(eventId)
                                     .thenCompose(event -> {
                                         if (event == null) {
                                             return CompletableFuture.failedFuture(new IllegalStateException(
                                                     "No StripeWebhookEvent found for id " + eventId));
                                         }
                                         event.setStatus(status)
                                              .setLastError(lastError);
                                         if (status == StripeWebhookEventStatus.PROCESSED) {
                                             event.setProcessedAt(new Date());
                                         }
                                         return webhookEventRepository.save(event).thenApply(saved -> null);
                                     });
    }
}
