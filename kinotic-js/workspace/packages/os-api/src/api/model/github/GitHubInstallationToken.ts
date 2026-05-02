/**
 * What worker nodes receive when they ask for clone credentials. The token is a
 * short-lived GitHub installation access token scoped to a single repository with
 * {@code contents:read} permission; {@code expiresAt} is the absolute UTC instant
 * (epoch milliseconds).
 */
export class GitHubInstallationToken {
    /** Bearer token to send as {@code Authorization: Bearer <token>} on git over HTTPS. */
    public token: string = ''

    /** Absolute expiry (epoch milliseconds). Workers should not use the token beyond this point. */
    public expiresAt: number = 0

    /** {@code https://github.com/<owner>/<repo>.git} for the linked repo. */
    public cloneUrl: string = ''

    /** Default branch on the linked repo (e.g. {@code main}). */
    public defaultBranch: string = ''
}
