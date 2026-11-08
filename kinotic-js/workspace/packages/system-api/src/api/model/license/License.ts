import { EnterpriseFeature } from '@/api/model/license/EnterpriseFeature'
import { LicenseStatus } from '@/api/model/license/LicenseStatus'

/**
 * A verified Kinotic Enterprise License: who it is issued to, when it was issued and expires, and the Enterprise
 * Features it turns on until its grace period ends.
 */
export class License {

    /** The party the license is issued to. */
    public licensee: string

    /** When the license was issued, or null when its key does not say. */
    public issuedAt: string | null = null

    /** When the license expires. */
    public expiresAt: string

    /** When the license stops turning its features on, a grace period after it expires. */
    public graceEndsAt: string

    /** Where the license stands at the time it was handed out. */
    public status: LicenseStatus

    /** The Enterprise Features the license turns on while it is active or in its grace period. */
    public features: EnterpriseFeature[] = []

    constructor(licensee: string, expiresAt: string, graceEndsAt: string, status: LicenseStatus) {
        this.licensee = licensee
        this.expiresAt = expiresAt
        this.graceEndsAt = graceEndsAt
        this.status = status
    }
}
