/*
 * Copyright 2008-2023 the original author or authors.
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

import java.util.Locale;

/**
 * Enumeration for sort directions.
 *
 * @author Oliver Gierke
 */
public enum Direction {
    ASC, DESC;

    /**
     * Returns whether the direction is ascending.
     *
     * @return true if ascending
     * @since 1.13
     */
    public boolean isAscending() {
        return this.equals(ASC);
    }

    /**
     * Returns whether the direction is descending.
     *
     * @return true if descending
     * @since 1.13
     */
    public boolean isDescending() {
        return this.equals(DESC);
    }

    /**
     * Returns the {@link Direction} enum for the given {@link String} value.
     *
     * @param value to convert to a Direction
     * @throws IllegalArgumentException in case the given value cannot be parsed into an enum value.
     * @return the Direction
     */
    public static Direction fromString(String value) {

        try {
            return Direction.valueOf(value.toUpperCase(Locale.US));
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(
                    "Invalid value '%s' for orders given; Has to be either 'desc' or 'asc' (case insensitive)", value), e);
        }
    }
}
