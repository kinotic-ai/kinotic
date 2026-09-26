import { SYSTEM_API_ZONE, type WatchEvent } from '@kinotic-ai/management-api'
import type { IKinotic } from '@kinotic-ai/core'
import { CrudServiceProxy, FunctionalIterablePage, type ICrudServiceProxy, type IterablePage, type Page, type Pageable } from '@kinotic-ai/core'
import { VmNode } from '@/api/model/workload/VmNode'


export interface IVmNodeService extends ICrudServiceProxy<VmNode> {

    /**
     * Lists what happened to the node, newest first: each change of what it should be and of what it
     * reports, and each mark set beside them, with what caused it.
     * @param nodeId the id of the node
     * @param pageable the page to return
     * @return a Promise resolving to a page of ledger entries, empty when the node is not registered
     */
    findHistory(nodeId: string, pageable: Pageable): Promise<IterablePage<WatchEvent>>

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a Promise that resolves when the operation is complete
     */
    syncIndex(): Promise<void>

}

export class VmNodeServiceProxy extends CrudServiceProxy<VmNode> implements IVmNodeService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy(`${SYSTEM_API_ZONE}~org.kinotic.system.api.services.workload.VmNodeService`))
    }

    public async findHistory(nodeId: string, pageable: Pageable): Promise<IterablePage<WatchEvent>> {
        const page: Page<WatchEvent> = await this.findHistorySinglePage(nodeId, pageable)
        return new FunctionalIterablePage(pageable, page,
            (pageable: Pageable) => this.findHistorySinglePage(nodeId, pageable))
    }

    public findHistorySinglePage(nodeId: string, pageable: Pageable): Promise<Page<WatchEvent>> {
        return this.serviceProxy.invoke('findHistory', [nodeId, pageable])
    }

    public syncIndex(): Promise<void> {
        return this.serviceProxy.invoke('syncIndex', [])
    }

}
