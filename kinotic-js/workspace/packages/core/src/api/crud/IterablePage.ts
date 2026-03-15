import type {Page} from '@/api/crud/Page'

/**
 * Defines a page that is also an async iterator.
 * This allows for easy iteration over all pages of a result set.
 */
export interface IterablePage<T> extends Page<T>, AsyncIterableIterator<IterablePage<T>> {

    /**
     * @return true if this is the last page, false otherwise.
     */
    isLastPage(): boolean

    /**
     * @return true if this page has content, false otherwise.
     */
    hasContent(): boolean

}
