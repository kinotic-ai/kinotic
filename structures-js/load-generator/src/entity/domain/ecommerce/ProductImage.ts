import { ProductImageType } from './ProductImageType'

export class ProductImage {
    public url!: string
    public type!: ProductImageType
    public altText?: string
    public width?: number
    public height?: number
    public size?: number // in bytes
    public format?: string // e.g., 'jpg', 'png', 'webp'
    public isDefault!: boolean
    public sortOrder!: number
    public variantId?: string // for variant-specific images

    protected constructor() {}

    static create(): ProductImage {
        return new ProductImage()
    }

    static builder(): ProductImageBuilder {
        return new ProductImageBuilder()
    }
}

export class ProductImageBuilder {
    private image: ProductImage = ProductImage.create()

    withUrl(url: string): ProductImageBuilder {
        this.image.url = url
        return this
    }

    withType(type: ProductImageType): ProductImageBuilder {
        this.image.type = type
        return this
    }

    withAltText(altText: string): ProductImageBuilder {
        this.image.altText = altText
        return this
    }

    withDimensions(width: number, height: number): ProductImageBuilder {
        this.image.width = width
        this.image.height = height
        return this
    }

    withSize(size: number): ProductImageBuilder {
        this.image.size = size
        return this
    }

    withFormat(format: string): ProductImageBuilder {
        this.image.format = format
        return this
    }

    withIsDefault(isDefault: boolean): ProductImageBuilder {
        this.image.isDefault = isDefault
        return this
    }

    withSortOrder(sortOrder: number): ProductImageBuilder {
        this.image.sortOrder = sortOrder
        return this
    }

    withVariantId(variantId: string): ProductImageBuilder {
        this.image.variantId = variantId
        return this
    }

    build(): ProductImage {
        return this.image
    }
} 