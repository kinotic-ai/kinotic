package org.kinotic.billing.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;

/**
 * A period statement for an organization: opening/closing balances plus the revenue splits
 * and ledger entries that explain the difference. This is the customer-facing transparency
 * artifact — every number on it traces to a {@link RevenueSplit} or {@link LedgerEntry}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class EarningsStatement {

    private String organizationId;

    private Date periodStart;

    private Date periodEnd;

    private long openingBalance;

    private long closingBalance;

    private List<RevenueSplit> splits;

    private List<LedgerEntry> entries;

}
