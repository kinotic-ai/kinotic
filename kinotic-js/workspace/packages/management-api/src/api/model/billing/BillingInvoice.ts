import type { InvoiceStatus } from '@/api/model/billing/InvoiceStatus'

/**
 * One of an organization's Stripe invoices. Amounts are in the smallest unit of currency.
 */
export interface BillingInvoice {
    id: string
    /** The number the invoice shows, null while it is a DRAFT. */
    number: string | null
    status: InvoiceStatus | null
    currency: string
    /** The invoice's total after discounts and tax. */
    total: number
    amountPaid: number
    /** What is still owed. */
    amountDue: number
    created: string | null
    /** The start of the period the invoice's usage was measured over. */
    periodStart: string | null
    periodEnd: string | null
    /** The Stripe page where the invoice is viewed and paid, once finalized. */
    hostedInvoiceUrl: string | null
    /** The invoice's PDF, once finalized. */
    invoicePdf: string | null
}
