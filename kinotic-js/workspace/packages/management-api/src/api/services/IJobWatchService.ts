import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { Observable } from 'rxjs'
import type { JobRunEvent } from '@/api/model/grind/events/JobRunEvent'

/**
 * Live view of grind job runs executing on one node. A run's event stream exists only in the
 * process executing it, so every node publishes its own instance of this service, scoped by
 * node id - a watch request is routed to the node recorded on JobRun.nodeId of the run being
 * watched.
 */
export interface IJobWatchService {

    /**
     * Opens a live view of a job run the participant may view, replaying every JobRunEvent
     * emitted since the run started and continuing until the run terminates. Completes
     * without emissions when the run is not currently executing on the node.
     * @param nodeId the id of the node executing the run, from JobRun.nodeId
     * @param jobRunId the id of the run to watch
     */
    watch(nodeId: string, jobRunId: string): Observable<JobRunEvent>
}

export class JobWatchService implements IJobWatchService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.JobWatchService`)
    }

    public watch(nodeId: string, jobRunId: string): Observable<JobRunEvent> {
        return this.serviceProxy.invokeStream('watch', [jobRunId], nodeId)
    }
}
