import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { Observable } from 'rxjs'
import type { LogQuery } from '@/api/model/telemetry/LogQuery'
import type { ServerLogQuery } from '@/api/model/telemetry/ServerLogQuery'

/**
 * Streams and queries the logs of workloads by the organization they ran for, and of the platform's
 * own servers. For workloads, an organization participant reads its own organization's, a system
 * participant reads any organization's, or the platform's own when it names no organization. A
 * workload's logs outlive the workload, so a destroyed one's are read the same way. Only a system
 * participant reads a server's logs. Every method yields the raw Loki response bytes; the caller
 * parses Loki's wire format.
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

    /**
     * Opens a live tail of a platform server's logs, one node's or every node's together. Each
     * emission is a raw Loki tail frame, and the stream stays open until unsubscribed.
     * @param telemetryServiceName the service name that labels the server's logs, as its cluster
     *        nodes report it
     * @param telemetryServiceInstanceId the service instance id that labels one node's logs, as
     *        that cluster node reports it; null for every node of the server
     */
    tailServer(telemetryServiceName: string, telemetryServiceInstanceId: string | null): Observable<Uint8Array>

    /**
     * Returns a platform server's historical logs, one node's or every node's together, as the raw
     * Loki query_range response.
     * @param query the server, the node, the time range, and the limit
     */
    serverHistory(query: ServerLogQuery): Promise<Uint8Array>
}

export class LogService implements ILogService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.telemetry.LogService`)
    }

    public tail(organizationId: string | null, workloadId: string): Observable<Uint8Array> {
        return this.serviceProxy.invokeStream('tail', [organizationId, workloadId])
    }

    public history(query: LogQuery): Promise<Uint8Array> {
        return this.serviceProxy.invoke('history', [query])
    }

    public tailServer(telemetryServiceName: string, telemetryServiceInstanceId: string | null): Observable<Uint8Array> {
        return this.serviceProxy.invokeStream('tailServer', [telemetryServiceName, telemetryServiceInstanceId])
    }

    public serverHistory(query: ServerLogQuery): Promise<Uint8Array> {
        return this.serviceProxy.invoke('serverHistory', [query])
    }
}
