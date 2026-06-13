import { CrudServiceProxy, FunctionalIterablePage, type IKinotic, type ICrudServiceProxy, type IterablePage, type Page, type Pageable } from '@kinotic-ai/core'
import { Workload } from '@/api/model/workload/Workload'


export interface IWorkloadService extends ICrudServiceProxy<Workload> {

    /**
     * Finds all workloads deployed on the given node.
     * @param nodeId the id of the node to find workloads for
     * @param pageable the page to return
     * @return a Promise resolving to a page of workloads
     */
    findAllForNode(nodeId: string, pageable: Pageable): Promise<IterablePage<Workload>>

    /**
     * Counts all workloads deployed on the given node.
     * @param nodeId the id of the node to count workloads for
     * @return a Promise resolving to the number of workloads
     */
    countForNode(nodeId: string): Promise<number>

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a Promise that resolves when the operation is complete
     */
    syncIndex(): Promise<void>

}

export class WorkloadServiceProxy extends CrudServiceProxy<Workload> implements IWorkloadService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy('org.kinotic.os.api.services.WorkloadService'))
    }

    public async findAllForNode(nodeId: string, pageable: Pageable): Promise<IterablePage<Workload>> {
        const page: Page<Workload> = await this.findAllForNodeSinglePage(nodeId, pageable)
        return new FunctionalIterablePage(pageable, page,
            (pageable: Pageable) => this.findAllForNodeSinglePage(nodeId, pageable))
    }

    public findAllForNodeSinglePage(nodeId: string, pageable: Pageable): Promise<Page<Workload>> {
        return this.serviceProxy.invoke('findAllForNode', [nodeId, pageable])
    }

    public countForNode(nodeId: string): Promise<number> {
        return this.serviceProxy.invoke('countForNode', [nodeId])
    }

    public syncIndex(): Promise<void> {
        return this.serviceProxy.invoke('syncIndex', [])
    }

}
