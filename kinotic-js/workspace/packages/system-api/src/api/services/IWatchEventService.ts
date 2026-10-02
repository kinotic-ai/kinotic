import { SYSTEM_API_ZONE, type WatchEvent } from '@kinotic-ai/management-api'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import { FunctionalIterablePage, type IterablePage, type Page, type Pageable } from '@kinotic-ai/core'

/**
 * The ledger of every watched record on the platform.
 */
export interface IWatchEventService {

    /**
     * Lists what happened to every watched record, newest first: each change of what a record should
     * be and of what it reports, each mark set beside them, and each status a run passed through, with
     * what caused it.
     * @param pageable the page to return
     * @return a Promise resolving to a page of ledger entries
     */
    findAll(pageable: Pageable): Promise<IterablePage<WatchEvent>>

}

export class WatchEventService implements IWatchEventService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${SYSTEM_API_ZONE}~org.kinotic.system.api.services.WatchEventService`)
    }

    public async findAll(pageable: Pageable): Promise<IterablePage<WatchEvent>> {
        const page: Page<WatchEvent> = await this.findAllSinglePage(pageable)
        return new FunctionalIterablePage(pageable, page, (pageable: Pageable) => this.findAllSinglePage(pageable))
    }

    private findAllSinglePage(pageable: Pageable): Promise<Page<WatchEvent>> {
        return this.serviceProxy.invoke('findAll', [pageable])
    }

}
