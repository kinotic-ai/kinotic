import type {Identifiable} from '@/api/crud/Identifiable'
import {AbstractIterablePage} from '@/api/crud/AbstractIterablePage'
import {CrudServiceProxy} from '@/api/crud/CrudServiceProxy'
import type {Page} from '@/api/crud/Page'
import {Pageable} from '@/api/crud/Pageable'

/**
 * {@link IterablePage} for use when finding all
 */
export class FindAllIterablePage<T extends Identifiable<string>> extends AbstractIterablePage<T> {

    private readonly crudServiceProxy: CrudServiceProxy<T>

    constructor(pageable: Pageable,
                page: Page<T>,
                crudServiceProxy: CrudServiceProxy<T>) {
        super(pageable, page)
        this.crudServiceProxy = crudServiceProxy
    }

    protected findNext(pageable: Pageable): Promise<Page<T>> {
        return this.crudServiceProxy.findAllSinglePage(pageable)
    }

}
