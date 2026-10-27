import type { RepoHostType } from '@/api/model/RepoHostType'

/**
 * Where projects' repositories are hosted.
 */
export interface RepositoryHost {

    /** The host of every project's repository. */
    type: RepoHostType

    /**
     * The base URL of the host's web pages as a person's browser reaches it (no trailing slash): a repository's page is
     * this URL followed by `/` and its `repoFullName`.
     */
    webBaseUrl: string
}
