import type { VulnerabilityFinding } from '@kinotic-ai/management-api'

/**
 * The advisories affecting the libraries one build of a Kinotic server runs, with the cluster nodes
 * running that build. Mirrors the server's org.kinotic.system.api.model.cluster.ServerVulnerabilities.
 */
export class ServerVulnerabilities {
    /** The server kind the build is, such as management, system or app. */
    public serverName: string = ''
    /** The Kinotic version of the build; null when its nodes run from classes rather than a packaged jar. */
    public version: string | null = null
    /** How many nodes run the build. */
    public nodeCount: number = 0
    /** How many libraries the build runs; none when its nodes' classpath holds no library list, such as when an IDE runs them from classes. */
    public componentCount: number = 0
    /** The advisories affecting the build's libraries, the most severe first. */
    public findings: VulnerabilityFinding[] = []
    /** Why the build's libraries could not be checked, or null when they were; a build that could not be checked has no findings. */
    public error: string | null = null
}
