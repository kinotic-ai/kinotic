package org.kinotic.management.api.model.billing;

/**
 * Where a {@link BillingInvoice} stands, as Stripe has it.
 */
public enum InvoiceStatus {
    /** Still being prepared; its amounts may change. */
    DRAFT,
    /** Finalized and awaiting payment. */
    OPEN,
    PAID,
    /** Payment is not expected to be collected. */
    UNCOLLECTIBLE,
    /** Cancelled; nothing is owed on it. */
    VOID
}
