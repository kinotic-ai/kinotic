package org.kinotic.billing.internal.api.services;

import org.apache.commons.lang3.Validate;

/**
 * Computes the money split for one settled charge. All amounts are minor currency units.
 * <p>
 * Invariant: {@code netToOrganization = amountTotal − taxAmount − stripeFeeAmount − platformFeeAmount}.
 */
public final class RevenueSplitCalculator {

    private static final long BASIS_POINT_DENOMINATOR = 10_000;

    /**
     * @param amountTotal            full charge amount, tax-inclusive; must be ≥ 0
     * @param taxAmount              tax collected for remittance; must be ≥ 0 and ≤ {@code amountTotal}
     * @param stripeFeeAmount        exact Stripe processing fee from the balance transaction; must be ≥ 0
     * @param platformFeeBasisPoints Kinotic take rate in basis points; must be in [0, 10000]
     */
    public static RevenueSplitAmounts compute(long amountTotal,
                                              long taxAmount,
                                              long stripeFeeAmount,
                                              long platformFeeBasisPoints) {
        Validate.isTrue(amountTotal >= 0, "amountTotal cannot be negative: %d", amountTotal);
        Validate.isTrue(taxAmount >= 0, "taxAmount cannot be negative: %d", taxAmount);
        Validate.isTrue(stripeFeeAmount >= 0, "stripeFeeAmount cannot be negative: %d", stripeFeeAmount);
        Validate.isTrue(platformFeeBasisPoints >= 0 && platformFeeBasisPoints <= BASIS_POINT_DENOMINATOR,
                        "platformFeeBasisPoints must be in [0, 10000]: %d", platformFeeBasisPoints);
        Validate.isTrue(taxAmount <= amountTotal,
                        "taxAmount %d cannot exceed amountTotal %d", taxAmount, amountTotal);

        // Tax is remitted, never revenue — the take rate applies to the tax-exclusive amount.
        // Integer division floors, so any fractional cent stays with the organization.
        long platformFeeAmount = (amountTotal - taxAmount) * platformFeeBasisPoints / BASIS_POINT_DENOMINATOR;
        long netToOrganization = amountTotal - taxAmount - stripeFeeAmount - platformFeeAmount;
        return new RevenueSplitAmounts(platformFeeAmount, netToOrganization);
    }

    private RevenueSplitCalculator() {
    }
}
