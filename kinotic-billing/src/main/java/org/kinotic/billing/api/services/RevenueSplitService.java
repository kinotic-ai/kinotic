package org.kinotic.billing.api.services;

import org.kinotic.billing.api.model.RevenueSplit;
import org.kinotic.core.api.crud.IdentifiableCrudService;

import java.util.concurrent.CompletableFuture;

/**
 * Records the charge-side lifecycle of the revenue-share ledger: settled charges become
 * {@link RevenueSplit}s plus ledger earnings; refunds and disputes claw them back.
 */
public interface RevenueSplitService extends IdentifiableCrudService<RevenueSplit, String> {

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

    /**
     * Records a refund against a previously recorded charge, debiting the organization's
     * share from the ledger.
     */
    CompletableFuture<RevenueSplit> recordRefund(String stripeChargeId, String stripeRefundId);

    /**
     * Freezes the organization's share of a charge while a dispute is open.
     */
    CompletableFuture<RevenueSplit> recordDisputeOpened(String stripeChargeId, String stripeDisputeId);

    /**
     * Settles a closed dispute: releases the hold when won, converts it to a permanent
     * debit when lost.
     */
    CompletableFuture<RevenueSplit> recordDisputeClosed(String stripeChargeId, String stripeDisputeId);
}
