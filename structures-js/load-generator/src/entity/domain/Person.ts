import {Entity, Id, MultiTenancyType, Precision, PrecisionType} from '@mindignited/structures-api'
import {Address} from './Address.js'


@Entity(MultiTenancyType.SHARED)
export class Person {

    @Id
    public id!: string

    public firstName!: string

    public lastName!: string

    public birthDate!: Date

    @Precision(PrecisionType.SHORT)
    public age?: number

    public address!: Address

}
