import { PrescriptionStatus } from './PrescriptionStatus'

export class Prescription {
    public medicationName!: string
    public dosageForm!: string
    public strength!: string
    public frequency!: string
    public quantity!: number
    public refillsRemaining: number = 0
    public startDate!: Date
    public endDate!: Date
    public instructions!: string
    public pharmacyName!: string
    public pharmacyPhone!: string
    public status: PrescriptionStatus = PrescriptionStatus.ACTIVE
    public notes!: string
    public isActive: boolean = true

    protected constructor() {}

    static create(): Prescription {
        return new Prescription()
    }

    static builder(): PrescriptionBuilder {
        return new PrescriptionBuilder()
    }
}

export class PrescriptionBuilder {
    private prescription: Prescription = Prescription.create()

    withMedicationName(medicationName: string): PrescriptionBuilder {
        this.prescription.medicationName = medicationName
        return this
    }

    withDosageForm(dosageForm: string): PrescriptionBuilder {
        this.prescription.dosageForm = dosageForm
        return this
    }

    withStrength(strength: string): PrescriptionBuilder {
        this.prescription.strength = strength
        return this
    }

    withFrequency(frequency: string): PrescriptionBuilder {
        this.prescription.frequency = frequency
        return this
    }

    withQuantity(quantity: number): PrescriptionBuilder {
        this.prescription.quantity = quantity
        return this
    }

    withRefillsRemaining(refillsRemaining: number): PrescriptionBuilder {
        this.prescription.refillsRemaining = refillsRemaining
        return this
    }

    withStartDate(startDate: Date): PrescriptionBuilder {
        this.prescription.startDate = startDate
        return this
    }

    withEndDate(endDate: Date): PrescriptionBuilder {
        this.prescription.endDate = endDate
        return this
    }

    withInstructions(instructions: string): PrescriptionBuilder {
        this.prescription.instructions = instructions
        return this
    }

    withPharmacyName(pharmacyName: string): PrescriptionBuilder {
        this.prescription.pharmacyName = pharmacyName
        return this
    }

    withPharmacyPhone(pharmacyPhone: string): PrescriptionBuilder {
        this.prescription.pharmacyPhone = pharmacyPhone
        return this
    }

    withStatus(status: PrescriptionStatus): PrescriptionBuilder {
        this.prescription.status = status
        return this
    }

    withNotes(notes: string): PrescriptionBuilder {
        this.prescription.notes = notes
        return this
    }

    withIsActive(isActive: boolean): PrescriptionBuilder {
        this.prescription.isActive = isActive
        return this
    }

    build(): Prescription {
        return this.prescription
    }
} 