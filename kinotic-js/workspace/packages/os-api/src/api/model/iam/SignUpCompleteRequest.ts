/**
 * Sent to {@code POST /api/auth/org/signup/complete} after the user clicks the verification link.
 * The {@link token} comes from that link; {@link orgName} (with optional {@link orgDescription})
 * names the organization to create, and {@link password} is the chosen account password.
 */
export interface SignUpCompleteRequest {
    token: string
    orgName: string
    orgDescription?: string | null
    password: string
}
