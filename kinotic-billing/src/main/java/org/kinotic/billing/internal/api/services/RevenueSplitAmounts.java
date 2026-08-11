package org.kinotic.billing.internal.api.services;

/**
 * Result of a revenue-split computation, in minor currency units.
 * {@code netToOrganization} may be negative when fees exceed the charge.
 */
public record RevenueSplitAmounts(long platformFeeAmount, long netToOrganization) {
}
