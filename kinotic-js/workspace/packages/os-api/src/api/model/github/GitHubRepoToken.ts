import { GitHubToken } from '@/api/model/github/GitHubToken'

/**
 * A {@link GitHubToken} bundled with the repository-clone metadata a worker needs
 * to {@code git clone} the project's backing repo. Returned by
 * {@code GitHubProjectRepoService.issueRepoToken}.
 */
export class GitHubRepoToken extends GitHubToken {
    /** {@code https://github.com/<owner>/<repo>.git} for the project's repo. */
    public cloneUrl: string = ''

    /** Default branch on the repo (e.g. {@code main}). */
    public defaultBranch: string = ''
}
