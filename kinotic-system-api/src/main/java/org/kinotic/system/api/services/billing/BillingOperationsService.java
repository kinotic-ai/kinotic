package org.kinotic.system.api.services.billing;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.billing.BillingAccount;
import org.kinotic.management.api.model.billing.BillingForecast;
import org.kinotic.management.api.model.billing.BillingInvoice;
import org.kinotic.management.api.model.billing.BillingPlan;
import org.kinotic.management.api.model.billing.UsageRecord;

import java.util.Date;
import java.util.List;

/**
 * The billing of one organization through Stripe, behind the management server's {@code BillingService}. Published
 * in the system zone, where the management server reaches it through {@code BillingOperationsProxy}. Callers are
 * trusted: the management plane names the calling member's own organization, and this service checks nothing about
 * the caller.
 */
@Publish
public interface BillingOperationsService {

    /**
     * The organization's billing account, created the first time it is read with a free trial that ends
     * {@code kinotic.managementApi.billing.trialDays} after the organization was created. A trial that has ended with
     * no subscription leaves the account past due since the trial's end.
     *
     * @param organizationId the organization
     * @return the account as it stands now; fails when no organization has the id
     */
    Future<BillingAccount> findAccount(String organizationId);

    /**
     * The plans the organization may subscribe to: those offered to every organization or to it alone, none archived,
     * ordered by name.
     */
    Future<List<BillingPlan>> findPlans(String organizationId);

    /**
     * Opens a Stripe Checkout page that subscribes the organization to the plan and returns to the portal's
     * organization settings. A subscription started more than two days before the free trial ends is first charged
     * when the trial ends. Only the page opened last can subscribe the organization: opening one expires the page
     * opened before it.
     *
     * @param organizationId the organization
     * @param planId         one of the plans {@link #findPlans(String)} returns for the organization
     * @return the URL of the Checkout page; fails when the organization subscribes already, when a page it completed
     *         is still being confirmed by Stripe, or when the plan is not one it may subscribe to
     */
    Future<String> createCheckoutSession(String organizationId, String planId);

    /**
     * Opens the Stripe Customer Portal for the organization, which returns to the portal's organization settings.
     *
     * @param organizationId the organization
     * @return the URL of the Customer Portal page; fails when the organization has never started a Checkout
     */
    Future<String> createPortalSession(String organizationId);

    /**
     * The organization's hourly usage records of the hours starting from {@code from} up to {@code to}, oldest first.
     * An hour in which the organization used nothing has no record.
     *
     * @param organizationId the organization
     * @param from           the start of the range
     * @param to             the end of the range, after {@code from} and at most 93 days after it
     * @return the records in the range
     */
    Future<List<UsageRecord>> findUsage(String organizationId, Date from, Date to);

    /**
     * What the organization's current billing period is expected to cost: its usage so far, plus what it runs and
     * stores now kept up to the period's end.
     *
     * @param organizationId the organization
     * @return the forecast, or {@code null} when the organization has no subscription or its free trial is still
     *         running
     */
    Future<BillingForecast> findForecast(String organizationId);

    /**
     * The organization's invoices, newest first: the last 24 of them.
     *
     * @param organizationId the organization
     * @return the invoices, empty when the organization has never started a Checkout
     */
    Future<List<BillingInvoice>> findInvoices(String organizationId);
}
