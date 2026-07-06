/**
 * Sent to {@code POST /api/auth/org/signup/social/complete} to finish an OIDC sign-up by naming the
 * organization to create. The verified identity is held server-side, keyed by {@link token}.
 */
export interface CompleteOrgRequest {
    token: string
    orgName: string
    orgDescription?: string | null
}
