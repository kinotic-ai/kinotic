import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { Observable } from 'rxjs'
import type { LogQuery } from '@/api/model/log/LogQuery'

/**
 * Streams and queries the logs of workloads by the organization they ran for: an organization
 * participant reads its own organization's, a system participant reads any organization's, or the
 * platform's own when it names no organization. A workload's logs outlive the workload, so a
 * destroyed one's are read the same way. Both methods yield the raw Loki response bytes; the
 * caller parses Loki's wire format.
 */
export interface ILogService {

    /**
     * Opens a live tail of the given workload's logs. Each emission is a raw Loki tail frame,
     * and the stream stays open until unsubscribed.
     * @param organizationId the organization the workload runs for; null for the platform's own,
     *        which only a system participant may read
     * @param workloadId the id of the workload to follow
     */
    tail(organizationId: string | null, workloadId: string): Observable<Uint8Array>

    /**
     * Returns a workload's historical logs as the raw Loki query_range response.
     * @param query the organization, workload, time range, and limit
     */
    history(query: LogQuery): Promise<Uint8Array>
}

export class LogService implements ILogService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.LogService`)
    }

    public tail(organizationId: string | null, workloadId: string): Observable<Uint8Array> {
        return this.serviceProxy.invokeStream('tail', [organizationId, workloadId])
    }

    public history(query: LogQuery): Promise<Uint8Array> {
        return this.serviceProxy.invoke('history', [query])
    }
}
