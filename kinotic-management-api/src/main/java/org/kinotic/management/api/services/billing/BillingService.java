package org.kinotic.management.api.services.billing;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.billing.BillingAccount;
import org.kinotic.management.api.model.billing.BillingPlan;

import java.util.List;

/**
 * The caller's organization's billing for Kinotic Cloud: its account, the plans it may subscribe to, and the Stripe
 * pages where it subscribes and manages payment. The organization is the authenticated participant's. Published only
 * where {@code kinotic.managementApi.billing.enabled}.
 */
@Publish
public interface BillingService {

    /**
     * The organization's billing account, created with a free trial the first time any organization member reads it.
     *
     * @return {@link Future} emitting the account as it stands now
     */
    Future<BillingAccount> findAccount();

    /**
     * The plans the organization may subscribe to: every plan offered to all organizations and those offered to it
     * alone, none archived.
     *
     * @return {@link Future} emitting the plans, ordered by name
     */
    Future<List<BillingPlan>> findPlans();

    /**
     * Starts a subscription to a plan: a Stripe Checkout page, where the organization enters its payment details,
     * that returns to the portal's organization settings. A subscription started with more than two days of the free
     * trial left is first charged when the trial ends; otherwise it is charged at once.
     *
     * @param planId one of the plans {@link #findPlans()} returns
     * @return {@link Future} emitting the URL of the Checkout page; fails when the organization already subscribes
     */
    Future<String> createCheckoutSession(String planId);

    /**
     * Opens the Stripe Customer Portal, where the organization updates its payment method, downloads its invoices and
     * cancels its subscription, and which returns to the portal's organization settings.
     *
     * @return {@link Future} emitting the URL of the Customer Portal page; fails when the organization has never started
     *         a Checkout
     */
    Future<String> createPortalSession();
}
