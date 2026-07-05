package org.kinotic.billing.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.OrganizationScoped;

import java.util.Date;

/**
 * One append-only journal line on an organization's payable balance. Entries are never
 * mutated; corrections are new {@link LedgerEntryType#ADJUSTMENT} entries. The
 * organization's balances are derived by aggregating entries, never stored.
 * <p>
 * The {@code id} is deterministic — {@code stripeObjectId + ":" + entryType} — so replaying
 * the same source event produces the same entry rather than a duplicate.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class LedgerEntry implements OrganizationScoped<String> {

    private String id;

    private String organizationId;

    /** Application the entry is attributed to; null for org-level entries (e.g. floor fees). */
    private String applicationId;

    private LedgerEntryType entryType;

    /** Signed amount in minor currency units. Credits are positive, debits negative. */
    private long amount;

    private String currency;

    /** The Stripe object this entry derives from (charge, refund, dispute, transfer id). */
    private String stripeObjectId;

    /** The {@link RevenueSplit} this entry decomposes, when charge-derived. */
    private String revenueSplitId;

    /** The {@link Payout} that settled this entry, when payout-derived. */
    private String payoutId;

    private String memo;

    private Date created;

}
