/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.core.api.crud;

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
