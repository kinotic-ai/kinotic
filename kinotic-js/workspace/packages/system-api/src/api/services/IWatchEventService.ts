import { SYSTEM_API_ZONE, type WatchEvent, type WatchedType } from '@kinotic-ai/management-api'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import { FunctionalIterablePage, type IterablePage, type Page, type Pageable } from '@kinotic-ai/core'

/**
 * The ledger of every watched record on the platform.
 */
export interface IWatchEventService {

    /**
     * Lists what happened to every watched record, newest first: each change of what a record should
     * be and of what it reports, each mark set beside them, and each status a run passed through, with
     * what caused it. Each entry names the record by its name when written, beside its id.
     * @param types the kinds of record to list the entries of, every kind when null or empty
     * @param pageable the page to return
     * @return a Promise resolving to a page of ledger entries
     */
    findAll(types: WatchedType[] | null, pageable: Pageable): Promise<IterablePage<WatchEvent>>

}

export class WatchEventService implements IWatchEventService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${SYSTEM_API_ZONE}~org.kinotic.system.api.services.WatchEventService`)
    }

    public async findAll(types: WatchedType[] | null, pageable: Pageable): Promise<IterablePage<WatchEvent>> {
        const page: Page<WatchEvent> = await this.findAllSinglePage(types, pageable)
        return new FunctionalIterablePage(pageable, page, (pageable: Pageable) => this.findAllSinglePage(types, pageable))
    }

    private findAllSinglePage(types: WatchedType[] | null, pageable: Pageable): Promise<Page<WatchEvent>> {
        return this.serviceProxy.invoke('findAll', [types, pageable])
    }

}
