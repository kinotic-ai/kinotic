import { PaymentStatus } from './PaymentStatus'
import { PaymentMethod } from './PaymentMethod'

/**
 * Represents a payment made by a customer.
 */
export class Payment {
    public method!: PaymentMethod
    public transactionId!: string
    public status!: PaymentStatus
    public amount!: number
    public currency!: string
    public lastFour!: string

    protected constructor() {}

    static create(): Payment {
        return new Payment()
    }

    static builder(): PaymentBuilder {
        return new PaymentBuilder()
    }
}

export class PaymentBuilder {
    private payment: Payment = Payment.create()

    withMethod(method: PaymentMethod): PaymentBuilder {
        this.payment.method = method
        return this
    }

    withTransactionId(transactionId: string): PaymentBuilder {
        this.payment.transactionId = transactionId
        return this
    }

    withStatus(status: PaymentStatus): PaymentBuilder {
        this.payment.status = status
        return this
    }

    withAmount(amount: number): PaymentBuilder {
        this.payment.amount = amount
        return this
    }

    withCurrency(currency: string): PaymentBuilder {
        this.payment.currency = currency
        return this
    }

    withLastFour(lastFour: string): PaymentBuilder {
        this.payment.lastFour = lastFour
        return this
    }

    build(): Payment {
        return this.payment
    }
} 