package org.kinotic.continuum.core.api.crud;

/**
 * {@link Pageable} is used to represent a request for a page of data.
 * Created by Navíd Mitchell 🤪 on 11/5/23.
 */
public interface Pageable {

    /**
     * @return the number of elements to return in the page
     */
    int getPageSize();

    /**
     * @return the {@link Sort} to use when sorting the results or null if no sort is specified
     */
    Sort getSort();

    /**
     * Creates a new {@link Pageable} with the given page size
     * @param pageNumber the page number to start at
     * @param pageSize the number of elements to return in the page
     * @param sort the {@link Sort} to use when sorting the results
     * @return a new {@link Pageable}
     */
    static OffsetPageable create(int pageNumber, int pageSize, Sort sort) {
        return new OffsetPageable(pageNumber, pageSize, sort);
    }

    /**
     * Creates a new {@link Pageable} with the given page size
     * @param pageSize the number of elements to return in the page
     * @return a new {@link Pageable}
     */
    static OffsetPageable ofSize(int pageSize) {
        return new OffsetPageable(0, pageSize, null);
    }

    /**
     * Creates a new {@link Pageable} with the given cursor
     * @param cursor the cursor to start at or null if this is the first page
     * @param pageSize the number of elements to return in the page
     * @param sort the {@link Sort} to use when sorting the results
     * @return a new {@link Pageable}
     */
    static CursorPageable create(String cursor, int pageSize, Sort sort) {
        return new CursorPageable(cursor, pageSize, sort);
    }
}

