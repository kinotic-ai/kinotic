

package org.kinotic.core.api.services.crud;

/**
 * Created by Navíd Mitchell 🤪 on 7/30/21.
 */
public class SearchCriteria<T>{

    private String key;
    private T value;
    private SearchComparator searchComparator;

    public SearchCriteria(String key, T value, SearchComparator searchComparator) {
        this.key = key;
        this.value = value;
        this.searchComparator = searchComparator;
    }

    public SearchCriteria() {
    }

    public String getKey() {
        return key;
    }

    public SearchCriteria<T> setKey(String key) {
        this.key = key;
        return this;
    }

    public T getValue() {
        return value;
    }

    public SearchCriteria<T> setValue(T value) {
        this.value = value;
        return this;
    }

    public SearchComparator getSearchComparator() {
        return searchComparator;
    }

    public SearchCriteria<T> setSearchComparator(SearchComparator searchComparator) {
        this.searchComparator = searchComparator;
        return this;
    }
}
