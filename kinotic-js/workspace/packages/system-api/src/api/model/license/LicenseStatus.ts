/**
 * Where a Kinotic Enterprise License stands now.
 */
export enum LicenseStatus {
    /** Before its expiry: its features are on. */
    ACTIVE = 'ACTIVE',
    /** Expired less than the grace period ago: its features stay on until the grace period ends. */
    GRACE_PERIOD = 'GRACE_PERIOD',
    /** Its grace period has ended: its features are off. */
    EXPIRED = 'EXPIRED'
}
