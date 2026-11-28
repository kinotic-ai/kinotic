import { MANAGEMENT_API_ZONE } from '@/api/Zones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import { map, type Observable } from 'rxjs'
import type { LogQuery } from '@/api/model/telemetry/LogQuery'
import type { ServerLogQuery } from '@/api/model/telemetry/ServerLogQuery'
import type { LokiRangeResponse } from '@/api/model/telemetry/LokiRangeResponse'
import type { LokiTailFrame } from '@/api/model/telemetry/LokiTailFrame'
import { parseJsonBytes } from '@/api/services/telemetry/parseJsonBytes'

/**
 * Streams and queries the logs of workloads by the organization they ran for, and of the Kinotic
 * servers. For workloads, an organization participant reads its own organization's, a system
 * participant reads any organization's, or the system's own when it names no organization. A
 * workload's logs outlive the workload, so a destroyed one's are read the same way. Only a system
 * participant reads a server's logs. Every method yields Loki's own response, in Loki's wire format.
 */
export interface ILogService {

    /**
     * Opens a live tail of the given workload's logs from start: the entries since start, then each
     * new one as it arrives. A history whose range ends at start reads what came before, with no entry
     * read twice. Each emission is a Loki tail frame, and the stream stays open until unsubscribed.
     * @param organizationId the organization the workload runs for; null for the system's own,
     *        which only a system participant may read
     * @param workloadId the id of the workload to follow
     * @param start the moment to follow from, epoch milliseconds (inclusive)
     */
    tail(organizationId: string | null, workloadId: string, start: number): Observable<LokiTailFrame>

    /**
     * Returns a workload's historical logs as the Loki query_range response.
     * @param query the organization, workload, time range, and limit
     */
    history(query: LogQuery): Promise<LokiRangeResponse>

    /**
     * Opens a live tail of a Kinotic server's logs, one node's or every node's together, from start:
     * the entries since start, then each new one as it arrives. A serverHistory whose range ends at
     * start reads what came before, with no entry read twice. Each emission is a Loki tail frame, and
     * the stream stays open until unsubscribed.
     * @param telemetryServiceName the service name that labels the server's logs, as its cluster
     *        nodes report it
     * @param telemetryServiceInstanceId the service instance id that labels one node's logs, as
     *        that cluster node reports it; null for every node of the server
     * @param start the moment to follow from, epoch milliseconds (inclusive)
     */
    tailServer(telemetryServiceName: string, telemetryServiceInstanceId: string | null, start: number): Observable<LokiTailFrame>

    /**
     * Returns a Kinotic server's historical logs, one node's or every node's together, as the Loki
     * query_range response.
     * @param query the server, the node, the time range, and the limit
     */
    serverHistory(query: ServerLogQuery): Promise<LokiRangeResponse>
}

export class LogService implements ILogService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.telemetry.LogService`)
    }

    public tail(organizationId: string | null, workloadId: string, start: number): Observable<LokiTailFrame> {
        return this.serviceProxy.invokeStream('tail', [organizationId, workloadId, start]).pipe(map(parseJsonBytes))
    }

    public async history(query: LogQuery): Promise<LokiRangeResponse> {
        return parseJsonBytes(await this.serviceProxy.invoke('history', [query]))
    }

    public tailServer(telemetryServiceName: string, telemetryServiceInstanceId: string | null, start: number): Observable<LokiTailFrame> {
        return this.serviceProxy.invokeStream('tailServer', [telemetryServiceName, telemetryServiceInstanceId, start]).pipe(map(parseJsonBytes))
    }

    public async serverHistory(query: ServerLogQuery): Promise<LokiRangeResponse> {
        return parseJsonBytes(await this.serviceProxy.invoke('serverHistory', [query]))
    }
}
