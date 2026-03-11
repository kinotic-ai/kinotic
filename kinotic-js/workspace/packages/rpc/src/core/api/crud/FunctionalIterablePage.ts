import {AbstractIterablePage} from "./AbstractIterablePage"
import {Page} from "./Page"
import {Pageable} from "./Pageable"

export class FunctionalIterablePage<T> extends AbstractIterablePage<T> {

    private readonly pageFunction: (pageable: Pageable) => Promise<Page<T>>

    constructor(pageable: Pageable,
                page: Page<T>,
                pageFunction: (pageable: Pageable) => Promise<Page<T>>) {
        super(pageable, page)
        this.pageFunction = pageFunction
    }

    protected findNext(pageable: Pageable): Promise<Page<T>> {
        return this.pageFunction(pageable)
    }

}
