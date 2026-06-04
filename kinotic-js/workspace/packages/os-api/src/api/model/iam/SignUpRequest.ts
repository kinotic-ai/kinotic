/**
 * Sent to {@code POST /api/signup} to start an email/password organization sign-up.
 * Only the user's identity is collected up front; the organization name and password are
 * provided after email verification (see {@link SignUpCompleteRequest}).
 */
export interface SignUpRequest {
    email: string
    displayName: string
}
