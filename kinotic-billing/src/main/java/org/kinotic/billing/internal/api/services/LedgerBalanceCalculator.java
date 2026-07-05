package org.kinotic.billing.internal.api.services;

import org.kinotic.billing.api.model.LedgerBalance;
import org.kinotic.billing.api.model.LedgerEntryType;

import java.util.Map;

/**
 * Derives an organization's {@link LedgerBalance} from per-type sums of its ledger entries.
 */
public final class LedgerBalanceCalculator {

    /**
     * @param sums signed amount sums per entry type, as produced by the ledger aggregation;
     *             types with no entries may be absent
     */
    public static LedgerBalance fromSums(String organizationId, Map<LedgerEntryType, Long> sums) {
        // The journal's signed amounts already net out: the sum of everything is what could
        // be paid out right now. Holds are negative entries whose release counterparts are
        // positive, so an outstanding hold is the (negated) sum of the pair.
        long availableForPayout = sums.values().stream().mapToLong(Long::longValue).sum();
        long disputeHeld = Math.max(0, -(sumOf(sums, LedgerEntryType.DISPUTE_HOLD)
                + sumOf(sums, LedgerEntryType.DISPUTE_RELEASE)));
        long reserved = Math.max(0, -(sumOf(sums, LedgerEntryType.RESERVE_HOLD)
                + sumOf(sums, LedgerEntryType.RESERVE_RELEASE)));
        long payable = availableForPayout + disputeHeld + reserved;
        return new LedgerBalance()
                .setOrganizationId(organizationId)
                .setPayable(payable)
                .setReserved(reserved)
                .setDisputeHeld(disputeHeld)
                .setAvailableForPayout(availableForPayout);
    }

    private static long sumOf(Map<LedgerEntryType, Long> sums, LedgerEntryType type) {
        return sums.getOrDefault(type, 0L);
    }

    private LedgerBalanceCalculator() {
    }
}
