export class PurchaseItem {
    public productId!: string
    public sku!: string
    public name!: string
    public quantity!: number
    public unitPrice!: number
    public totalPrice!: number

    protected constructor() {}

    static create(): PurchaseItem {
        return new PurchaseItem()
    }

    static builder(): PurchaseItemBuilder {
        return new PurchaseItemBuilder()
    }
}

export class PurchaseItemBuilder {
    private item: PurchaseItem = PurchaseItem.create()

    withProductId(productId: string): PurchaseItemBuilder {
        this.item.productId = productId
        return this
    }

    withSku(sku: string): PurchaseItemBuilder {
        this.item.sku = sku
        return this
    }

    withName(name: string): PurchaseItemBuilder {
        this.item.name = name
        return this
    }

    withQuantity(quantity: number): PurchaseItemBuilder {
        this.item.quantity = quantity
        return this
    }

    withUnitPrice(unitPrice: number): PurchaseItemBuilder {
        this.item.unitPrice = unitPrice
        return this
    }

    withTotalPrice(totalPrice: number): PurchaseItemBuilder {
        this.item.totalPrice = totalPrice
        return this
    }

    build(): PurchaseItem {
        return this.item
    }
} 