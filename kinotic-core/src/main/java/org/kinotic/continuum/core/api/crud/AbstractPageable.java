package org.kinotic.continuum.core.api.crud;

/**
 * Base class for {@link Pageable} implementations.
 * Created by Navíd Mitchell 🤪 on 11/6/23.
 */
public abstract class AbstractPageable implements Pageable {

    protected final int pageSize;
    protected final Sort sort;

    protected AbstractPageable(int pageSize, Sort sort) {
        this.pageSize = pageSize;
        this.sort = sort;
    }

    /**
     * @return the number of elements to return in the page
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * @return the {@link Sort} to use when sorting the results
     */
    public Sort getSort() {
        return sort;
    }
}
