/**
 * A short-lived GitHub installation access token. Mirrors the Java
 * {@code org.kinotic.management.api.model.github.GitHubToken}; {@code expiresAt} is the
 * absolute UTC instant, as an ISO-8601 timestamp, at which GitHub will reject the token.
 */
export class GitHubToken {
    /** Bearer token to send as {@code Authorization: Bearer <token>}. */
    public token: string = ''

    /** Absolute expiry, as an ISO-8601 timestamp. Do not use past this point. */
    public expiresAt: string = ''
}
