package org.kinotic.billing.api.services;

import org.kinotic.billing.api.model.RevenueSplit;

import java.util.concurrent.CompletableFuture;

/**
 * Records the charge-side lifecycle of the revenue-share ledger: settled charges become
 * {@link RevenueSplit}s plus ledger earnings.
 */
public interface RevenueSplitService {

    /**
     * Records the revenue split for a settled charge and credits the owning organization's
     * ledger. Idempotent: recording the same charge again returns the existing split without
     * posting a duplicate ledger entry.
     * <p>
     * Fails when the charge's balance transaction has not settled yet (the exact Stripe fee
     * is unknowable until then) — callers retry on failure.
     *
     * @param stripeChargeId the Stripe charge to record
     * @return the recorded (or pre-existing) split
     */
    CompletableFuture<RevenueSplit> recordCharge(String stripeChargeId);
}
