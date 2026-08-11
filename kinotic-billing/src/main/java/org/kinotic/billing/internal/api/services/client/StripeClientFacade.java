package org.kinotic.billing.internal.api.services.client;

import java.util.concurrent.CompletableFuture;

/**
 * The billing module's only doorway to the Stripe API. Keeps {@code com.stripe.model.*}
 * out of ledger code so the payment rail can be swapped or faked without touching callers.
 */
public interface StripeClientFacade {

    /**
     * Fetches a charge with its balance transaction resolved when available.
     *
     * @return the charge details, or {@code null} if no such charge exists
     */
    CompletableFuture<StripeChargeDetails> fetchChargeDetails(String stripeChargeId);
}
