package org.kinotic.billing.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * An organization's derived ledger balances at a point in time, computed by aggregating
 * {@link LedgerEntry} amounts — never stored.
 * <p>
 * Invariant: {@code availableForPayout = payable − reserved − disputeHeld}. All amounts are
 * minor currency units.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class LedgerBalance {

    private String organizationId;

    /** Everything Kinotic owes the org, including funds under reserve or dispute hold. */
    private long payable;

    /** Portion of {@link #payable} withheld as rolling reserve. */
    private long reserved;

    /** Portion of {@link #payable} frozen by open disputes. */
    private long disputeHeld;

    private long availableForPayout;

}
