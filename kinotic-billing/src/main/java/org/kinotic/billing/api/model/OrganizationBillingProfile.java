package org.kinotic.billing.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.OrganizationScoped;

import java.util.Date;

/**
 * Billing configuration for one organization: which payment rail it uses, its Stripe
 * recipient account, pricing plan, payout schedule, and reserve policy.
 * <p>
 * The {@code id} always equals {@code organizationId} — one profile per organization.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OrganizationBillingProfile implements OrganizationScoped<String> {

    private String id;

    private String organizationId;

    private MerchantOfRecord merchantOfRecord;

    /** The org's Stripe recipient connected account ({@code acct_...}); null until created. */
    private String stripeRecipientAccountId;

    private RecipientAccountStatus recipientAccountStatus;

    private String pricingPlanId;

    private PayoutInterval payoutInterval;

    /** Payout sweeps skip the org while payable − reserve is below this (minor units). */
    private long minimumPayoutAmount;

    /** Rolling reserve withheld from earnings, in basis points (0 = no reserve). */
    private long reserveBasisPoints;

    /** Days earnings must age before they are eligible for a payout sweep. */
    private int reserveHoldDays;

    private OrgBillingStatus billingStatus;

    private Date created;

    private Date updated;

}
