import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { Observable } from 'rxjs'
import type { LogQuery } from '@/api/model/log/LogQuery'

/**
 * Streams and queries container logs stored in Loki, scoped to the caller's organization.
 * Both methods yield the raw Loki response bytes; the caller parses Loki's wire format.
 */
export interface ILogService {

    /**
     * Opens a live tail of logs matching the LogQL query. Each emission is a raw Loki tail frame,
     * and the stream stays open until unsubscribed.
     * @param query the LogQL query selecting the log streams to follow
     */
    tail(query: string): Observable<Uint8Array>

    /**
     * Returns historical logs for the query and time range as the raw Loki query_range response.
     * @param query the LogQL selector, time range, and limit
     */
    history(query: LogQuery): Promise<Uint8Array>
}

export class LogService implements ILogService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy('org.kinotic.os.api.services.LogService')
    }

    public tail(query: string): Observable<Uint8Array> {
        return this.serviceProxy.invokeStream('tail', [query])
    }

    public history(query: LogQuery): Promise<Uint8Array> {
        return this.serviceProxy.invoke('history', [query])
    }
}
