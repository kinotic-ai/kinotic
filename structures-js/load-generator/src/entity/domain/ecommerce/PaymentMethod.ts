import { PaymentMethodType } from './PaymentMethodType'

export class PaymentMethod {
    public type!: PaymentMethodType
    public lastFour!: string
    public expiryMonth!: number
    public expiryYear!: number
    public isDefault!: boolean

    protected constructor() {}

    static create(): PaymentMethod {
        return new PaymentMethod()
    }

    static builder(): PaymentMethodBuilder {
        return new PaymentMethodBuilder()
    }
}

export class PaymentMethodBuilder {
    private paymentMethod: PaymentMethod = PaymentMethod.create()

    withType(type: PaymentMethodType): PaymentMethodBuilder {
        this.paymentMethod.type = type
        return this
    }

    withLastFour(lastFour: string): PaymentMethodBuilder {
        this.paymentMethod.lastFour = lastFour
        return this
    }

    withExpiryMonth(expiryMonth: number): PaymentMethodBuilder {
        this.paymentMethod.expiryMonth = expiryMonth
        return this
    }

    withExpiryYear(expiryYear: number): PaymentMethodBuilder {
        this.paymentMethod.expiryYear = expiryYear
        return this
    }

    withIsDefault(isDefault: boolean): PaymentMethodBuilder {
        this.paymentMethod.isDefault = isDefault
        return this
    }

    build(): PaymentMethod {
        return this.paymentMethod
    }
} 