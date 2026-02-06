import { ContactMethod } from './ContactMethod'

export class ContactInfo {
    public email!: string
    public phoneNumber!: string
    public emergencyContactName!: string

    public emergencyContactPhone: string = ''

    public emergencyContactRelationship: string = ''

    public preferredContactMethod: ContactMethod = ContactMethod.EMAIL

    public isPrimary: boolean = false

    protected constructor() {}

    static create(): ContactInfo {
        return new ContactInfo()
    }

    static builder(): ContactInfoBuilder {
        return new ContactInfoBuilder()
    }
}

export class ContactInfoBuilder {
    private contactInfo: ContactInfo = ContactInfo.create()

    withEmail(email: string): ContactInfoBuilder {
        this.contactInfo.email = email
        return this
    }

    withPhoneNumber(phoneNumber: string): ContactInfoBuilder {
        this.contactInfo.phoneNumber = phoneNumber
        return this
    }

    withEmergencyContactName(emergencyContactName: string): ContactInfoBuilder {
        this.contactInfo.emergencyContactName = emergencyContactName
        return this
    }

    withEmergencyContactPhone(emergencyContactPhone: string): ContactInfoBuilder {
        this.contactInfo.emergencyContactPhone = emergencyContactPhone
        return this
    }

    withEmergencyContactRelationship(emergencyContactRelationship: string): ContactInfoBuilder {
        this.contactInfo.emergencyContactRelationship = emergencyContactRelationship
        return this
    }

    withPreferredContactMethod(preferredContactMethod: ContactMethod): ContactInfoBuilder {
        this.contactInfo.preferredContactMethod = preferredContactMethod
        return this
    }

    withIsPrimary(isPrimary: boolean): ContactInfoBuilder {
        this.contactInfo.isPrimary = isPrimary
        return this
    }

    build(): ContactInfo {
        return this.contactInfo
    }
} 