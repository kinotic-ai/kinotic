/**
 * Where an invoice stands, as Stripe has it.
 */
export enum InvoiceStatus {
    /** Still being prepared; its amounts may change. */
    DRAFT = 'DRAFT',
    /** Finalized and awaiting payment. */
    OPEN = 'OPEN',
    PAID = 'PAID',
    /** Payment is not expected to be collected. */
    UNCOLLECTIBLE = 'UNCOLLECTIBLE',
    /** Cancelled; nothing is owed on it. */
    VOID = 'VOID'
}
