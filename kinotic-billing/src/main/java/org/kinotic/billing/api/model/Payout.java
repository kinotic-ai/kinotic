package org.kinotic.billing.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.OrganizationScoped;

import java.util.Date;

/**
 * One batched transfer of an organization's earned balance to its Stripe recipient account.
 * <p>
 * The {@code id} doubles as the Stripe idempotency key and {@code transfer_group}, so a
 * re-driven payout can never move money twice.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Payout implements OrganizationScoped<String> {

    private String id;

    private String organizationId;

    /** Amount transferred, in minor currency units. */
    private long amount;

    private String currency;

    private String stripeTransferId;

    /** Ledger entries created at or before this instant are covered by this payout. */
    private Date cutoffDate;

    private PayoutStatus status;

    private Date created;

    private Date updated;

}
