/**
 * A short-lived GitHub installation access token. Mirrors the Java
 * {@code org.kinotic.github.api.model.GitHubToken}; {@code expiresAt} is the
 * absolute UTC instant (epoch milliseconds) at which GitHub will reject the token.
 */
export class GitHubToken {
    /** Bearer token to send as {@code Authorization: Bearer <token>}. */
    public token: string = ''

    /** Absolute expiry (epoch milliseconds). Do not use past this point. */
    public expiresAt: number = 0
}
