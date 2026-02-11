package org.kinotic.continuum.core.api.crud;

public class OffsetPageable extends AbstractPageable {

    private final int pageNumber;

    protected OffsetPageable(int pageNumber, int pageSize, Sort sort) {
        super(pageSize, sort);
        this.pageNumber = pageNumber;
    }

    /**
     * @return the page number to start at
     */
    public int getPageNumber() {
        return pageNumber;
    }
}
