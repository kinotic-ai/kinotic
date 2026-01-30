import {Entity, Id, MultiTenancyType, Text, Version, EntityType} from '@kinotic/structures-api'

export class Wheel {
    public brand!: string
    public size!: number
}

@Entity(MultiTenancyType.SHARED, EntityType.TABLE)
export class Vehicle {
    @Id
    public id!: string
    @Version
    public version: string | null = null
    public manufacturer!: string
    @Text
    public model!: string
    public color!: string
    public wheelType!: Wheel
}

