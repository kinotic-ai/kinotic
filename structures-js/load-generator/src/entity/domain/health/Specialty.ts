import { Text } from '@mindignited/structures-api'

export class Specialty {

    public name!: string

    @Text
    public description!: string

    public certificationRequired!: boolean

    public yearsOfTraining!: number

    public isActive!: boolean

    protected constructor() {}

    static create(): Specialty {
        return new Specialty()
    }

    static builder(): SpecialtyBuilder {
        return new SpecialtyBuilder()
    }
}

export class SpecialtyBuilder {
    private specialty: Specialty = Specialty.create()

    withName(name: string): SpecialtyBuilder {
        this.specialty.name = name
        return this
    }

    withDescription(description: string): SpecialtyBuilder {
        this.specialty.description = description
        return this
    }

    withCertificationRequired(certificationRequired: boolean): SpecialtyBuilder {
        this.specialty.certificationRequired = certificationRequired
        return this
    }

    withYearsOfTraining(yearsOfTraining: number): SpecialtyBuilder {
        this.specialty.yearsOfTraining = yearsOfTraining
        return this
    }

    withIsActive(isActive: boolean): SpecialtyBuilder {
        this.specialty.isActive = isActive
        return this
    }

    build(): Specialty {
        return this.specialty
    }
} 