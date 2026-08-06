/**
 * A live session of a delegate: one refresh-token family, described without exposing any token
 * material.
 */
export interface DelegateSession {
    /** Identifies the session for revocation. */
    familyId: string
    /** The label supplied when the session started (e.g. a device name), or null. */
    label: string | null
    /** When the session's current token was minted — issuance, or the latest rotation. */
    lastRefreshedAt: number
    /** When the session ends on its own if never refreshed again. */
    expiresAt: number
}
