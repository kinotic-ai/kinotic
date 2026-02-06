export class ProductAttribute {
    public key!: string
    public value!: string

    protected constructor() {}

    static create(): ProductAttribute {
        return new ProductAttribute()
    }

    static builder(): ProductAttributeBuilder {
        return new ProductAttributeBuilder()
    }
}

export class ProductAttributeBuilder {
    private attribute: ProductAttribute = ProductAttribute.create()

    withKey(key: string): ProductAttributeBuilder {
        this.attribute.key = key
        return this
    }

    withValue(value: string): ProductAttributeBuilder {
        this.attribute.value = value
        return this
    }

    build(): ProductAttribute {
        return this.attribute
    }
} 