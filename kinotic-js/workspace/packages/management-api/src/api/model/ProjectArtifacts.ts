import type { MicroserviceArtifact } from '@/api/model/MicroserviceArtifact'
import type { UiArtifact } from '@/api/model/UiArtifact'

/**
 * The artifacts one commit of a project contains, as the sync workload found them in the
 * checkout: what a deployment of that commit runs and publishes. Both lists are ordered by
 * name.
 */
export interface ProjectArtifacts {
    /**
     * The microservice artifacts, empty when the commit has none.
     */
    microservices: MicroserviceArtifact[]
    /**
     * The UI artifacts, empty when the commit has none.
     */
    uis: UiArtifact[]
}
