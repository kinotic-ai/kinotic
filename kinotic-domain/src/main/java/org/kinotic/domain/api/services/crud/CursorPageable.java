package org.kinotic.domain.api.services.crud;

/**
 * A {@link Pageable} that uses a cursor to track the current position in the result set.
 * Created by Navíd Mitchell 🤪 on 11/5/23.
 */
public class CursorPageable extends AbstractPageable {

    private final String cursor;

    protected CursorPageable(String cursor, int pageSize, Sort sort) {
        super(pageSize, sort);
        this.cursor = cursor;
    }

    /**
     * @return the cursor to start at
     */
    public String getCursor() {
        return cursor;
    }
}
