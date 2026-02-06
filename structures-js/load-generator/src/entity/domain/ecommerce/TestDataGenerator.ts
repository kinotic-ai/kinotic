import { faker } from '@faker-js/faker'
import { Customer } from './Customer'
import { Product } from './Product'
import { ProductReview } from './ProductReview'
import { Purchase } from './Purchase'
import { Address } from './Address'
import { PaymentMethod } from './PaymentMethod'
import { PaymentMethodType } from './PaymentMethodType'
import { Payment } from './Payment'
import { PaymentStatus } from './PaymentStatus'
import { PurchaseItem } from './PurchaseItem'
import { PurchaseStatus } from './PurchaseStatus'
import { ProductImage } from './ProductImage'
import { ProductImageType } from './ProductImageType'
import { ProductAttribute } from './ProductAttribute'

export class TestDataGenerator {
    private static readonly CATEGORIES = [
        'Electronics', 'Clothing', 'Books', 'Home & Garden', 'Sports',
        'Beauty', 'Toys', 'Automotive', 'Health', 'Jewelry'
    ]

    private static readonly BRANDS = [
        'Apple', 'Samsung', 'Nike', 'Adidas', 'Sony',
        'Microsoft', 'Amazon', 'Google', 'LG', 'Bose'
    ]

    static generateCustomer(): Customer {
        const customer = Customer.builder()
            .withEmail(faker.internet.email())
            .withFirstName(faker.person.firstName())
            .withLastName(faker.person.lastName())
            .withPhoneNumber(faker.phone.number())
            .withShippingAddress(this.generateAddress())
            .withBillingAddress(this.generateAddress())
            .withPaymentMethods([this.generatePaymentMethod()])
            .withTotalSpent(faker.number.float({ min: 0, max: 10000, fractionDigits: 2 }))
            .withPurchaseCount(faker.number.int({ min: 0, max: 50 }))
            .withFavoriteCategories(faker.helpers.arrayElements(this.CATEGORIES, { min: 1, max: 3 }))
            .withLastPurchaseDate(faker.date.recent())
            .withCreatedAt(faker.date.past())
            .withUpdatedAt(faker.date.recent())
            .build()

        return customer
    }

    static generateProduct(): Product {
        const product = Product.builder()
            .withName(faker.commerce.productName())
            .withDescription(faker.commerce.productDescription())
            .withSku(faker.string.alphanumeric(8).toUpperCase())
            .withPrice(faker.number.float({ min: 10, max: 1000, fractionDigits: 2 }))
            .withSalePrice(faker.number.float({ min: 5, max: 500, fractionDigits: 2 }))
            .withStockQuantity(faker.number.int({ min: 0, max: 1000 }))
            .withBrand(faker.helpers.arrayElement(this.BRANDS))
            .withCategory(faker.helpers.arrayElement(this.CATEGORIES))
            .withTags(faker.helpers.arrayElements(this.CATEGORIES, { min: 1, max: 5 }))
            .withAttributes([
                this.generateProductAttribute('Color', faker.color.human()),
                this.generateProductAttribute('Size', faker.helpers.arrayElement(['S', 'M', 'L', 'XL'])),
                this.generateProductAttribute('Material', faker.commerce.productMaterial())
            ])
            .withImages([
                this.generateProductImage(ProductImageType.MAIN),
                this.generateProductImage(ProductImageType.GALLERY),
                this.generateProductImage(ProductImageType.THUMBNAIL)
            ])
            .build()

        return product
    }

    static generateProductReview(productId: string, customerId: string): ProductReview {
        const review = ProductReview.builder()
            .withProductId(productId)
            .withCustomerId(customerId)
            .withTitle(faker.lorem.sentence())
            .withContent(faker.lorem.paragraphs(2))
            .withRating(faker.number.float({ min: 1, max: 5, fractionDigits: 1 }))
            .withVerifiedPurchase(faker.datatype.boolean())
            .withHelpfulVotes(faker.number.int({ min: 0, max: 100 }))
            .withUnhelpfulVotes(faker.number.int({ min: 0, max: 50 }))
            .withImages(faker.helpers.arrayElements([faker.image.url(), faker.image.url()], { min: 0, max: 2 }))
            .withCreatedAt(faker.date.recent())
            .withUpdatedAt(faker.date.recent())
            .build()

        return review
    }

    static generatePurchase(customerId: string, productIds: string[]): Purchase {
        const items = productIds.map(productId => this.generatePurchaseItem(productId))
        const subtotal = items.reduce((sum, item) => sum + item.totalPrice, 0)
        const tax = subtotal * 0.1 // 10% tax
        const shipping = faker.number.float({ min: 5, max: 20, fractionDigits: 2 })
        const discount = faker.number.float({ min: 0, max: subtotal * 0.2, fractionDigits: 2 }) // Up to 20% discount
        const total = subtotal + tax + shipping - discount

        const purchase = Purchase.builder()
            .withPurchaseNumber(faker.string.alphanumeric(10).toUpperCase())
            .withCustomerId(customerId)
            .withStatus(faker.helpers.arrayElement(Object.values(PurchaseStatus)))
            .withItems(items)
            .withSubtotal(subtotal)
            .withTax(tax)
            .withShipping(shipping)
            .withDiscount(discount)
            .withTotal(total)
            .withShippingAddress(this.generateAddress())
            .withBillingAddress(this.generateAddress())
            .withPayment(this.generatePayment(total))
            .withTrackingNumber(faker.string.alphanumeric(12).toUpperCase())
            .withCarrier(faker.helpers.arrayElement(['UPS', 'FedEx', 'USPS', 'DHL']))
            .withEstimatedDeliveryDate(faker.date.future())
            .withActualDeliveryDate(faker.date.recent())
            .withCreatedAt(faker.date.past())
            .withUpdatedAt(faker.date.recent())
            .build()

        return purchase
    }

    private static generateAddress(): Address {
        return Address.builder()
            .withStreet(faker.location.streetAddress())
            .withCity(faker.location.city())
            .withState(faker.location.state())
            .withPostalCode(faker.location.zipCode())
            .withCountry(faker.location.country())
            .build()
    }

    private static generatePaymentMethod(): PaymentMethod {
        return PaymentMethod.builder()
            .withType(faker.helpers.arrayElement(Object.values(PaymentMethodType)))
            .withLastFour(faker.string.numeric(4))
            .withExpiryMonth(faker.number.int({ min: 1, max: 12 }))
            .withExpiryYear(faker.number.int({ min: 2024, max: 2030 }))
            .withIsDefault(faker.datatype.boolean())
            .build()
    }

    private static generatePayment(amount: number): Payment {
        return Payment.builder()
            .withMethod(this.generatePaymentMethod())
            .withTransactionId(faker.string.alphanumeric(16).toUpperCase())
            .withStatus(faker.helpers.arrayElement(Object.values(PaymentStatus)))
            .withAmount(amount)
            .withCurrency('USD')
            .withLastFour(faker.string.numeric(4))
            .build()
    }

    private static generatePurchaseItem(productId: string): PurchaseItem {
        const quantity = faker.number.int({ min: 1, max: 5 })
        const unitPrice = faker.number.float({ min: 10, max: 500, fractionDigits: 2 })
        const totalPrice = quantity * unitPrice

        return PurchaseItem.builder()
            .withProductId(productId)
            .withSku(faker.string.alphanumeric(8).toUpperCase())
            .withName(faker.commerce.productName())
            .withQuantity(quantity)
            .withUnitPrice(unitPrice)
            .withTotalPrice(totalPrice)
            .build()
    }

    private static generateProductAttribute(key: string, value: string): ProductAttribute {
        return ProductAttribute.builder()
            .withKey(key)
            .withValue(value)
            .build()
    }

    private static generateProductImage(type: ProductImageType): ProductImage {
        return ProductImage.builder()
            .withUrl(faker.image.url())
            .withType(type)
            .withAltText(faker.lorem.sentence())
            .withDimensions(faker.number.int({ min: 800, max: 2000 }), faker.number.int({ min: 600, max: 1500 }))
            .withSize(faker.number.int({ min: 100000, max: 1000000 }))
            .withFormat(faker.helpers.arrayElement(['jpg', 'png', 'webp']))
            .withIsDefault(type === ProductImageType.MAIN)
            .withSortOrder(type === ProductImageType.MAIN ? 0 : faker.number.int({ min: 1, max: 10 }))
            .build()
    }

    static generateTestData(count: number = 20): {
        customers: Customer[],
        products: Product[],
        reviews: ProductReview[],
        purchases: Purchase[]
    } {
        const customers = Array.from({ length: count }, () => this.generateCustomer())
        const products = Array.from({ length: count }, () => this.generateProduct())
        
        // Generate reviews for each product from random customers
        const reviews = products.flatMap(product => 
            Array.from({ length: faker.number.int({ min: 1, max: 5 }) }, () => 
                this.generateProductReview(
                    product.id!,
                    faker.helpers.arrayElement(customers).id!
                )
            )
        )

        // Generate purchases for each customer with random products
        const purchases = customers.flatMap(customer => 
            Array.from({ length: faker.number.int({ min: 1, max: 3 }) }, () => 
                this.generatePurchase(
                    customer.id!,
                    faker.helpers.arrayElements(products, { min: 1, max: 5 }).map(p => p.id!)
                )
            )
        )

        return {
            customers,
            products,
            reviews,
            purchases
        }
    }
} 