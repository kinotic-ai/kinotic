/**
 * Sent to the server to initiate an organization sign-up.
 * The user provides {@link orgName}, {@link orgDescription}, {@link email}, and
 * {@link displayName}. The remaining fields ({@link id}, {@link verificationToken},
 * {@link expiresAt}, {@link created}) are populated by the server before the record
 * is persisted.
 */
export interface SignUpRequest {
    id?: string | null
    verificationToken?: string | null
    expiresAt?: number | null
    created?: number | null

    orgName: string
    orgDescription?: string | null
    email: string
    displayName: string
}
