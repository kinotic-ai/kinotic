package org.kinotic.billing.internal.api.services.client;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Billing-owned view of one Stripe charge: the amounts and attribution the ledger needs,
 * decoupled from the Stripe SDK's model classes.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class StripeChargeDetails {

    private String chargeId;

    private String paymentIntentId;

    private String invoiceId;

    private String endUserCustomerId;

    /** Full charge amount in minor units, tax-inclusive. */
    private long amountTotal;

    private String currency;

    private Boolean livemode;

    // Attribution from the metadata contract; null when the charge wasn't created by Kinotic.
    private String orgId;
    private String applicationId;
    private String tenantId;

    /**
     * Exact Stripe processing fee from the charge's balance transaction, or {@code null}
     * when the charge hasn't settled yet.
     */
    private Long stripeFeeAmount;

    /** Tax collected on the charge. Always 0 until Stripe Tax integration lands. */
    private long taxAmount;

}
