package org.kinotic.core.api.crud;

import java.util.List;
import java.util.function.Function;

/**
 * A {@link Page} defines a page of data.
 * Created by Navíd Mitchell 🤪 on 11/5/23.
 */
public class Page<T> {

    private final List<T> content;
    private final Long totalElements;

    public Page(List<T> content, Long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }

    /**
     * @return the total number of elements available from the data source, or null if not known.
     */
    public Long getTotalElements(){
        return totalElements;
    }

    /**
     * @return the page content as {@link List}.
     */
    public List<T> getContent(){
        return content;
    }

    /**
     * Returns a page whose content is this page's content transformed by {@code mapper},
     * carrying the same paging metadata.
     */
    public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
        return new Page<>(content.stream().<R>map(mapper).toList(), totalElements);
    }

}
