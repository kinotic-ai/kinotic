package org.kinotic.management.api.model.billing;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * One of an organization's Stripe invoices. Amounts are in the smallest unit of {@link #currency}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class BillingInvoice {

    private String id;
    /** The number the invoice shows, {@code null} while it is a {@link InvoiceStatus#DRAFT}. */
    private String number;
    private InvoiceStatus status;
    private String currency;
    /** The invoice's total after discounts and tax. */
    private long total;
    private long amountPaid;
    /** What is still owed. */
    private long amountDue;
    private Date created;
    /** The start of the period the invoice's usage was measured over. */
    private Date periodStart;
    private Date periodEnd;
    /** The Stripe page where the invoice is viewed and paid, once finalized. */
    private String hostedInvoiceUrl;
    /** The invoice's PDF, once finalized. */
    private String invoicePdf;
}
