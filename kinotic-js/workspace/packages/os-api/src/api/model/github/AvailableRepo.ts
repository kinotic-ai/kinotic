/**
 * One row in the repo dropdown shown when linking a project. Carries just enough
 * info for the UI; a successful link round-trips through
 * {@link IProjectGitHubRepoService} which re-validates against GitHub before
 * persisting.
 */
export class AvailableRepo {
    public repoId: number = 0
    public fullName: string = ''
    public defaultBranch: string = ''
    public privateRepo: boolean = false
}
