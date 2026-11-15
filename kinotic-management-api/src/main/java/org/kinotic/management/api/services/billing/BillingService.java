package org.kinotic.management.api.services.billing;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.billing.BillingAccount;
import org.kinotic.management.api.model.billing.BillingForecast;
import org.kinotic.management.api.model.billing.BillingInvoice;
import org.kinotic.management.api.model.billing.BillingPlan;
import org.kinotic.management.api.model.billing.UsageMeterDescriptor;
import org.kinotic.management.api.model.billing.UsageRecord;

import java.util.Date;
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
     * trial left is first charged when the trial ends; otherwise it is charged at once. Only the page opened last can
     * subscribe the organization: opening one expires the page opened before it.
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

    /**
     * Every meter usage is recorded under, in the order the portal shows them.
     *
     * @return {@link Future} emitting a descriptor of each meter
     */
    Future<List<UsageMeterDescriptor>> findMeters();

    /**
     * The organization's hourly usage of Kinotic Cloud, a line for each meter and thing that used it, for the hours
     * starting from {@code from} up to {@code to}. An hour in which the organization used nothing has no record.
     *
     * @param from the start of the range
     * @param to   the end of the range, after {@code from} and at most 93 days after it
     * @return {@link Future} emitting the records in the range, oldest first
     */
    Future<List<UsageRecord>> findUsage(Date from, Date to);

    /**
     * What the organization's current billing period is expected to cost: its usage so far, plus what it runs and
     * stores now kept up to the period's end.
     *
     * @return {@link Future} emitting the forecast, or {@code null} when the organization has no subscription or its
     *         free trial is still running
     */
    Future<BillingForecast> findForecast();

    /**
     * The organization's invoices, newest first: the last 24 of them, each with a link to view and pay it at Stripe.
     *
     * @return {@link Future} emitting the invoices, empty when the organization has never started a Checkout
     */
    Future<List<BillingInvoice>> findInvoices();
}
