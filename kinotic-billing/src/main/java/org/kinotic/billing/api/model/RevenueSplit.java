package org.kinotic.billing.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.OrganizationScoped;

import java.util.Date;

/**
 * The per-charge explanation of how an end-user payment was divided: what Stripe collected,
 * what tax was set aside, what Kinotic's platform fee was, and what the organization earned.
 * This is the transparency record an organization drills into from an earnings statement;
 * the corresponding money movement is the linked {@link LedgerEntry}.
 * <p>
 * The {@code id} is the Stripe charge id, making creation naturally idempotent.
 * <p>
 * Invariant: {@code netToOrganization = amountTotal − taxAmount − stripeFeeAmount − platformFeeAmount}.
 * All amounts are minor currency units; tax is remitted and is never anyone's revenue.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class RevenueSplit implements OrganizationScoped<String> {

    private String id;

    private String organizationId;

    private String applicationId;

    private String tenantId;

    private String stripePaymentIntentId;

    private String stripeInvoiceId;

    private String endUserStripeCustomerId;

    /** Full charge amount in minor units, tax-inclusive. */
    private long amountTotal;

    private long taxAmount;

    private long stripeFeeAmount;

    private long platformFeeAmount;

    /** Take rate applied, in basis points (500 = 5%). */
    private long platformFeeBasisPoints;

    private String pricingPlanId;

    private long netToOrganization;

    private RevenueSplitStatus status;

    private Date created;

    private Date updated;

}
