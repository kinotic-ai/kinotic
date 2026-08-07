package org.kinotic.core.api.crud;

import java.util.List;
import java.util.function.Function;

/**
 * A {@link Page} that uses a cursor to track the current position in the result set.
 * Created by Navíd Mitchell 🤪 on 11/5/23.
 */
public class CursorPage<T> extends Page<T> {

    private final String cursor;

    public CursorPage(List<T> content,
                      String cursor,
                      Long totalElements) {
        super(content, totalElements);
        this.cursor = cursor;
    }

    /**
     * @return The cursor to be used for subsequent retrieval of data, or null if this is the last page
     */
    public String getCursor(){
        return cursor;
    }

    @Override
    public <R> CursorPage<R> map(Function<? super T, ? extends R> mapper) {
        return new CursorPage<>(getContent().stream().<R>map(mapper).toList(), cursor, getTotalElements());
    }
}
