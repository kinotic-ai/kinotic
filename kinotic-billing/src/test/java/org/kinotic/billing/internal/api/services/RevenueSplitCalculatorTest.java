package org.kinotic.billing.internal.api.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RevenueSplitCalculatorTest {

    @Test
    public void whenComputingSplit_thenNetEqualsGrossMinusTaxMinusFees() {
        // The $50 / 5% worked example from the Stripe spike: 5000 − 175 − 250 = 4575
        RevenueSplitAmounts amounts = RevenueSplitCalculator.compute(5000, 0, 175, 500);
        assertEquals(250, amounts.platformFeeAmount());
        assertEquals(4575, amounts.netToOrganization());
    }

    @Test
    public void whenPlatformFeeComputed_thenBasisPointsApplyToNetOfTax() {
        // Tax is remitted, never revenue: fee = (10000 − 1000) * 5% = 450, not 500
        RevenueSplitAmounts amounts = RevenueSplitCalculator.compute(10000, 1000, 300, 500);
        assertEquals(450, amounts.platformFeeAmount());
        assertEquals(10000 - 1000 - 300 - 450, amounts.netToOrganization());
    }

    @Test
    public void whenPlatformFeeFractional_thenFeeFlooredInOrganizationsFavor() {
        // 999 * 500 / 10000 = 49.95 cents → floors to 49
        RevenueSplitAmounts amounts = RevenueSplitCalculator.compute(999, 0, 0, 500);
        assertEquals(49, amounts.platformFeeAmount());
        assertEquals(950, amounts.netToOrganization());
    }

    @Test
    public void whenFeesExceedGross_thenNetIsNegativeAndInvariantHolds() {
        long amountTotal = 100;
        long stripeFee = 175;
        RevenueSplitAmounts amounts = RevenueSplitCalculator.compute(amountTotal, 0, stripeFee, 500);
        assertTrue(amounts.netToOrganization() < 0);
        assertEquals(amountTotal - stripeFee - amounts.platformFeeAmount(),
                     amounts.netToOrganization());
    }

    @Test
    public void whenAnyInputNegative_thenThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> RevenueSplitCalculator.compute(-1, 0, 0, 500));
        assertThrows(IllegalArgumentException.class, () -> RevenueSplitCalculator.compute(100, -1, 0, 500));
        assertThrows(IllegalArgumentException.class, () -> RevenueSplitCalculator.compute(100, 0, -1, 500));
        assertThrows(IllegalArgumentException.class, () -> RevenueSplitCalculator.compute(100, 0, 0, -1));
    }

    @Test
    public void whenTaxExceedsTotal_thenThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> RevenueSplitCalculator.compute(100, 101, 0, 500));
    }

    @Test
    public void whenBasisPointsExceedWhole_thenThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> RevenueSplitCalculator.compute(100, 0, 0, 10_001));
    }
}
