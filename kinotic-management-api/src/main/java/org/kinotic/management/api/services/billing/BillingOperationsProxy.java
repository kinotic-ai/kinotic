package org.kinotic.management.api.services.billing;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Proxy;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.model.billing.BillingAccount;
import org.kinotic.management.api.model.billing.BillingForecast;
import org.kinotic.management.api.model.billing.BillingInvoice;
import org.kinotic.management.api.model.billing.BillingPlan;
import org.kinotic.management.api.model.billing.UsageRecord;

import java.util.Date;
import java.util.List;

/**
 * The management server's way to the system server's {@code BillingOperationsService}, which holds the Stripe
 * integration. Method for method the same contract; the management plane names the caller's own organization in
 * every call, since the service trusts its callers.
 */
@Proxy(namespace = "org.kinotic.system.api.services.billing", name = "BillingOperationsService")
@Zone(DomainUtil.SYSTEM_API_ZONE)
public interface BillingOperationsProxy {

    Future<BillingAccount> findAccount(String organizationId);

    Future<List<BillingPlan>> findPlans(String organizationId);

    Future<String> createCheckoutSession(String organizationId, String planId);

    Future<String> createPortalSession(String organizationId);

    Future<List<UsageRecord>> findUsage(String organizationId, Date from, Date to);

    Future<BillingForecast> findForecast(String organizationId);

    Future<List<BillingInvoice>> findInvoices(String organizationId);
}
