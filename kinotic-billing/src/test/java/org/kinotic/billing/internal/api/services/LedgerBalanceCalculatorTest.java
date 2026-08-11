package org.kinotic.billing.internal.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.billing.api.model.LedgerBalance;
import org.kinotic.billing.api.model.LedgerEntryType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LedgerBalanceCalculatorTest {

    @Test
    public void whenOnlyEarnings_thenPayableEqualsAvailableForPayout() {
        LedgerBalance balance = LedgerBalanceCalculator.fromSums("org1",
                Map.of(LedgerEntryType.EARNING, 10_000L));
        assertEquals(10_000, balance.getPayable());
        assertEquals(10_000, balance.getAvailableForPayout());
        assertEquals(0, balance.getReserved());
        assertEquals(0, balance.getDisputeHeld());
    }

    @Test
    public void whenDisputeHoldOutstanding_thenDisputeHeldReducesAvailable() {
        LedgerBalance balance = LedgerBalanceCalculator.fromSums("org1", Map.of(
                LedgerEntryType.EARNING, 10_000L,
                LedgerEntryType.DISPUTE_HOLD, -2_000L));
        assertEquals(2_000, balance.getDisputeHeld());
        assertEquals(8_000, balance.getAvailableForPayout());
        assertEquals(10_000, balance.getPayable());
    }

    @Test
    public void whenReserveHeldAndReleased_thenReservedIsZero() {
        LedgerBalance balance = LedgerBalanceCalculator.fromSums("org1", Map.of(
                LedgerEntryType.EARNING, 5_000L,
                LedgerEntryType.RESERVE_HOLD, -1_000L,
                LedgerEntryType.RESERVE_RELEASE, 1_000L));
        assertEquals(0, balance.getReserved());
        assertEquals(5_000, balance.getAvailableForPayout());
        assertEquals(5_000, balance.getPayable());
    }

    @Test
    public void whenMixedEntries_thenAvailableEqualsPayableMinusReservedMinusDisputeHeld() {
        LedgerBalance balance = LedgerBalanceCalculator.fromSums("org1", Map.of(
                LedgerEntryType.EARNING, 10_000L,
                LedgerEntryType.REFUND_CLAWBACK, -1_000L,
                LedgerEntryType.DISPUTE_HOLD, -2_000L,
                LedgerEntryType.DISPUTE_RELEASE, 500L,
                LedgerEntryType.RESERVE_HOLD, -800L,
                LedgerEntryType.TRANSFER, -3_000L));
        assertEquals(3_700, balance.getAvailableForPayout());
        assertEquals(1_500, balance.getDisputeHeld());
        assertEquals(800, balance.getReserved());
        assertEquals(6_000, balance.getPayable());
        assertEquals(balance.getPayable() - balance.getReserved() - balance.getDisputeHeld(),
                     balance.getAvailableForPayout());
    }

    @Test
    public void whenNoEntries_thenAllBalancesZero() {
        LedgerBalance balance = LedgerBalanceCalculator.fromSums("org1", Map.of());
        assertEquals(0, balance.getPayable());
        assertEquals(0, balance.getReserved());
        assertEquals(0, balance.getDisputeHeld());
        assertEquals(0, balance.getAvailableForPayout());
    }
}
