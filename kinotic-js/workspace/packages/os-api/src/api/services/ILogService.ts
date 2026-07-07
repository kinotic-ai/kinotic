import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { Observable } from 'rxjs'
import type { LogQuery } from '@/api/model/log/LogQuery'

/**
 * Streams and queries the logs of workloads the authenticated participant may view: an
 * organization participant sees its own organization's workloads, a system participant sees any.
 * Both methods yield the raw Loki response bytes; the caller parses Loki's wire format.
 */
export interface ILogService {

    /**
     * Opens a live tail of the given workload's logs. Each emission is a raw Loki tail frame,
     * and the stream stays open until unsubscribed.
     * @param workloadId the id of the workload to follow
     */
    tail(workloadId: string): Observable<Uint8Array>

    /**
     * Returns a workload's historical logs as the raw Loki query_range response.
     * @param query the workload, time range, and limit
     */
    history(query: LogQuery): Promise<Uint8Array>
}

export class LogService implements ILogService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy('os_api.org.kinotic.os.api.services.LogService')
    }

    public tail(workloadId: string): Observable<Uint8Array> {
        return this.serviceProxy.invokeStream('tail', [workloadId])
    }

    public history(query: LogQuery): Promise<Uint8Array> {
        return this.serviceProxy.invoke('history', [query])
    }
}
