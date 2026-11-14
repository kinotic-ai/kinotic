package org.kinotic.management.api.model.billing;

/**
 * What a {@link PlanComponent} charges for.
 */
public enum PlanComponentType {
    /** A fixed amount each billing interval. */
    FLAT,
    /** An amount for each unit of a {@link UsageMeter} used during the billing interval. */
    METERED
}
