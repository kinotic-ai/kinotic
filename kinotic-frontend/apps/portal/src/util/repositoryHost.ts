import { Kinotic } from '@kinotic-ai/core'
import { RepoHostType, type RepositoryHost } from '@kinotic-ai/management-api'

// Asked of the server once per page load: the host is the deployment's, the same for every organization
let host: Promise<RepositoryHost> | null = null

/** Where projects' repositories are hosted. */
export function repositoryHost(): Promise<RepositoryHost> {
  let ret = host
  if (ret === null) {
    ret = Kinotic.projects.getRepositoryHost().catch((err: unknown) => {
      host = null
      throw err
    })
    host = ret
  }
  return ret
}

/** Whether an organization links GitHub before it creates a project, which a host other than GitHub needs no linking for. */
export async function requiresGitHubLink(): Promise<boolean> {
  return (await repositoryHost()).type === RepoHostType.GITHUB
}

/** The web page of the repository with the given full name. */
export function repositoryUrl(repositoryHost: RepositoryHost, repoFullName: string): string {
  return `${repositoryHost.webBaseUrl}/${repoFullName}`
}
