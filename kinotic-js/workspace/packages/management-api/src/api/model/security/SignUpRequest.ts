/**
 * Sent to {@code POST /api/auth/org/signup} to start an email/password organization sign-up.
 * Only the email is collected up front; the founder's name, the organization name and the password are
 * provided after email verification (see {@link SignUpCompleteRequest}).
 */
export interface SignUpRequest {
    email: string
}
