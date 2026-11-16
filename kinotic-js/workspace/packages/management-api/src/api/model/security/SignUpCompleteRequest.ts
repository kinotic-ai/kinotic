/**
 * Sent to {@code POST /api/auth/org/signup/complete} after the user clicks the verification link.
 * The {@link token} comes from that link; {@link displayName} is the founder's name, {@link orgName}
 * (with optional {@link orgDescription}) names the organization to create, and {@link password} is the
 * founder's chosen password.
 */
export interface SignUpCompleteRequest {
    token: string
    displayName: string
    orgName: string
    orgDescription?: string | null
    password: string
}
