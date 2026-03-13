/**
 * A page is a sublist of a list of objects.
 * @author Navid Mitchell
 */
export interface Page<T> {

    /**
     * @return the total number of elements or null or undefined if not known.
     */
    readonly totalElements: number | null | undefined

    /**
     * The cursor to be used for subsequent retrieval of data.
     * @return an opaque string representation of the cursor, or null if this is the last page, or undefined if cursor paging is not being used.
     */
    readonly cursor: string | null | undefined

    /**
     * @return the page content as {@link Array} or null or undefined if no data is available.
     */
    readonly content: T[] | null | undefined

}
