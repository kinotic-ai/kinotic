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
public enum SearchComparator {
    EQUALS("="),
    NOT("!"),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUALS(">="),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUALS("<="),
    LIKE("~");

    private final String stringValue;

    SearchComparator(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    public static final SearchComparator fromStringValue(String stringValue){
        switch (stringValue) {
            case "=":
                return EQUALS;
            case "!":
                return NOT;
            case ">":
                return GREATER_THAN;
            case ">=":
                return GREATER_THAN_OR_EQUALS;
            case "<":
                return LESS_THAN;
            case "<=":
                return LESS_THAN_OR_EQUALS;
            case "~":
                return LIKE;
            default:
                throw new IllegalStateException("Unexpected value: " + stringValue);
        }
    }
}
