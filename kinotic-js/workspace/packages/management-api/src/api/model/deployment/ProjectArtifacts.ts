import type { MicroserviceArtifact } from '@/api/model/deployment/MicroserviceArtifact'
import type { UiArtifact } from '@/api/model/deployment/UiArtifact'

/**
 * The artifacts one commit of a project contains, as the sync workload found them in the
 * checkout: what a deployment of that commit runs and publishes, and a fingerprint of the
 * dependencies installed for them. Both lists are ordered by name.
 */
export interface ProjectArtifacts {
    /**
     * Full 40-character SHA of the commit the artifacts were found in.
     */
    commitSha: string
    /**
     * The microservice artifacts, empty when the commit has none.
     */
    microservices: MicroserviceArtifact[]
    /**
     * The UI artifacts, empty when the commit has none.
     */
    uis: UiArtifact[]
    /**
     * A fingerprint of the dependencies the checkout installed: a SHA-256 of its bun.lock and of
     * the SBOM generator's version, so two commits with the same fingerprint have the same
     * ProjectSbom. Null when the checkout has no bun.lock.
     */
    dependencyHash: string | null
}
