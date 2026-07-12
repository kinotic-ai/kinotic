package org.kinotic.billing.internal.endpoints;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.billing.api.model.RevenueSplit;
import org.kinotic.billing.api.model.StripeWebhookEvent;
import org.kinotic.billing.api.services.RevenueSplitService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StripeEventDispatcherTest {

    private RecordingRevenueSplitService revenueSplitService;
    private StripeEventDispatcher dispatcher;

    @BeforeEach
    public void setUp() {
        revenueSplitService = new RecordingRevenueSplitService();
        // expectedLivemode=false mirrors a test-mode deployment
        dispatcher = new StripeEventDispatcher(revenueSplitService, false);
    }

    private StripeWebhookEvent chargeSucceeded() {
        return new StripeWebhookEvent()
                .setId("evt_1")
                .setEventType("charge.succeeded")
                .setLivemode(false)
                .setRelatedObjectId("ch_123");
    }

    @Test
    public void whenChargeSucceeded_thenRecordChargeInvokedWithRelatedObjectId() {
        dispatcher.dispatch(chargeSucceeded()).join();
        assertEquals(List.of("ch_123"), revenueSplitService.recordedCharges);
    }

    @Test
    public void whenLivemodeMismatch_thenIgnoredAndNoHandlerInvoked() {
        // Stripe sends test events to live URLs and vice versa; both ack without processing
        dispatcher.dispatch(chargeSucceeded().setLivemode(true)).join();
        assertTrue(revenueSplitService.recordedCharges.isEmpty());
    }

    @Test
    public void whenUnknownEventType_thenCompletesSuccessfullyWithoutDispatch() {
        dispatcher.dispatch(chargeSucceeded().setEventType("customer.created")).join();
        assertTrue(revenueSplitService.recordedCharges.isEmpty());
    }

    @Test
    public void whenHandlerFails_thenDispatchFutureCompletesExceptionally() {
        revenueSplitService.failure = new IllegalStateException("boom");
        assertThrows(CompletionException.class, () -> dispatcher.dispatch(chargeSucceeded()).join());
    }

    @Test
    public void whenLivemodeNull_thenDispatchProceeds() {
        dispatcher.dispatch(chargeSucceeded().setLivemode(null)).join();
        assertEquals(List.of("ch_123"), revenueSplitService.recordedCharges);
    }

    @Test
    public void whenChargeSucceededWithoutRelatedObjectId_thenFails() {
        assertThrows(CompletionException.class,
                     () -> dispatcher.dispatch(chargeSucceeded().setRelatedObjectId(null)).join());
    }

    static class RecordingRevenueSplitService implements RevenueSplitService {
        final List<String> recordedCharges = new ArrayList<>();
        RuntimeException failure;

        @Override
        public CompletableFuture<RevenueSplit> recordCharge(String stripeChargeId) {
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            recordedCharges.add(stripeChargeId);
            return CompletableFuture.completedFuture(new RevenueSplit().setId(stripeChargeId));
        }
    }
}
