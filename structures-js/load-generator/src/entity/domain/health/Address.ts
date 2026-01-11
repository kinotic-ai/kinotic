import { Text } from '@mindignited/structures-api'

export class Address {
    @Text
    public streetAddress: string = ''

    public city: string = ''

    public state: string = ''

    public postalCode: string = ''

    public country: string = ''

    public isPrimary: boolean = false

    protected constructor() {}

    static create(): Address {
        return new Address()
    }

    static builder(): AddressBuilder {
        return new AddressBuilder()
    }
}

export class AddressBuilder {
    private address: Address = Address.create()

    withStreet(street: string): AddressBuilder {
        this.address.streetAddress = street
        return this
    }

    withCity(city: string): AddressBuilder {
        this.address.city = city
        return this
    }

    withState(state: string): AddressBuilder {
        this.address.state = state
        return this
    }

    withPostalCode(postalCode: string): AddressBuilder {
        this.address.postalCode = postalCode
        return this
    }

    withCountry(country: string): AddressBuilder {
        this.address.country = country
        return this
    }

    build(): Address {
        return this.address
    }
} 