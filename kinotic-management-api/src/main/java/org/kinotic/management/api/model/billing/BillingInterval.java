package org.kinotic.management.api.model.billing;

/**
 * How often a {@link BillingPlan} bills: its flat fee is charged, and its metered usage is invoiced, once an interval.
 */
public enum BillingInterval {
    MONTH,
    YEAR
}
