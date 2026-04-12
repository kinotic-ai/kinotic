/**
 * Sent to the server to complete an organization sign-up after email verification.
 * The {@link token} comes from the verification email link; the {@link password}
 * is what the user enters on the "set your password" form.
 */
export interface SignUpCompleteRequest {
    token: string
    password: string
}
