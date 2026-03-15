import type {Identifiable} from '@/api/crud/Identifiable'
import {AbstractIterablePage} from '@/api/crud/AbstractIterablePage'
import {CrudServiceProxy} from '@/api/crud/CrudServiceProxy'
import type {Page} from '@/api/crud/Page'
import {Pageable} from '@/api/crud/Pageable'

/**
 * {@link IterablePage} for use when searching
 */
export class SearchIterablePage<T extends Identifiable<string>> extends AbstractIterablePage<T> {

    private readonly searchText: string
    private readonly crudServiceProxy: CrudServiceProxy<T>

    constructor(pageable: Pageable,
                page: Page<T>,
                searchText: string,
                crudServiceProxy: CrudServiceProxy<T>) {
        super(pageable, page)
        this.searchText = searchText
        this.crudServiceProxy = crudServiceProxy
    }

    protected findNext(pageable: Pageable): Promise<Page<T>> {
        return this.crudServiceProxy.searchSinglePage(this.searchText, pageable)
    }

}
