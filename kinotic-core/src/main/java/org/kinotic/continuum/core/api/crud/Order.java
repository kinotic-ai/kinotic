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
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * PropertyPath implements the pairing of an {@link Direction} and a property. It is used to provide input for
 * {@link Sort}
 *
 * @author Oliver Gierke
 * @author Kevin Raymond
 */
public class Order {
    private static final boolean DEFAULT_IGNORE_CASE = false;
    private static final NullHandling DEFAULT_NULL_HANDLING = NullHandling.NATIVE;

    private final Direction direction;
    private final String property;
    private final boolean ignoreCase;
    private final NullHandling nullHandling;

    /**
     * Creates a new {@link Order} instance. if order is {@literal null} then order defaults to
     * {@link Sort#DEFAULT_DIRECTION}
     *
     * @param direction can be {@literal null}, will default to {@link Sort#DEFAULT_DIRECTION}
     * @param property must not be {@literal null} or empty.
     */
    public Order(@Nullable Direction direction, String property) {
        this(direction, property, DEFAULT_IGNORE_CASE, DEFAULT_NULL_HANDLING);
    }

    /**
     * Creates a new {@link Order} instance. if order is {@literal null} then order defaults to
     * {@link Sort#DEFAULT_DIRECTION}
     *
     * @param direction can be {@literal null}, will default to {@link Sort#DEFAULT_DIRECTION}
     * @param property must not be {@literal null} or empty.
     * @param nullHandlingHint must not be {@literal null}.
     */
    public Order(@Nullable Direction direction, String property, NullHandling nullHandlingHint) {
        this(direction, property, DEFAULT_IGNORE_CASE, nullHandlingHint);
    }

    /**
     * Creates a new {@link Order} instance. Takes a single property. Direction defaults to
     * {@link Sort#DEFAULT_DIRECTION}.
     *
     * @param property must not be {@literal null} or empty.
     */
    public static Order by(String property) {
        return new Order(Sort.DEFAULT_DIRECTION, property);
    }

    /**
     * Creates a new {@link Order} instance. Takes a single property. Direction is {@link Direction#ASC} and
     * NullHandling {@link NullHandling#NATIVE}.
     *
     * @param property must not be {@literal null} or empty.
     */
    public static Order asc(String property) {
        return new Order(Direction.ASC, property, DEFAULT_NULL_HANDLING);
    }

    /**
     * Creates a new {@link Order} instance. Takes a single property. Direction is {@link Direction#DESC} and
     * NullHandling {@link NullHandling#NATIVE}.
     *
     * @param property must not be {@literal null} or empty.
     */
    public static Order desc(String property) {
        return new Order(Direction.DESC, property, DEFAULT_NULL_HANDLING);
    }

    /**
     * Creates a new {@link Order} instance. if order is {@literal null} then order defaults to
     * {@link Sort#DEFAULT_DIRECTION}
     *
     * @param direction can be {@literal null}, will default to {@link Sort#DEFAULT_DIRECTION}
     * @param property must not be {@literal null} or empty.
     * @param ignoreCase true if sorting should be case-insensitive. false if sorting should be case-sensitive.
     * @param nullHandling must not be {@literal null}.
     */
    private Order(@Nullable Direction direction, String property, boolean ignoreCase, NullHandling nullHandling) {

        if (!StringUtils.hasText(property)) {
            throw new IllegalArgumentException("Property must not be null or empty");
        }

        this.direction = direction == null ? Sort.DEFAULT_DIRECTION : direction;
        this.property = property;
        this.ignoreCase = ignoreCase;
        this.nullHandling = nullHandling;
    }

    /**
     * Returns the order the property shall be sorted for.
     * @return the direction
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * Returns the property to order for.
     * @return the property
     */
    public String getProperty() {
        return property;
    }

    /**
     * Returns whether sorting for this property shall be ascending.
     * @return true if ascending
     */
    public boolean isAscending() {
        return this.direction.isAscending();
    }

    /**
     * Returns whether sorting for this property shall be descending.
     * @return true if descending
     * @since 1.13
     */
    public boolean isDescending() {
        return this.direction.isDescending();
    }

    /**
     * Returns whether the sort will be case-sensitive or case-insensitive.
     * @return true if sorting should be case-insensitive.
     */
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    /**
     * Returns a new {@link Order} with the given {@link Direction}.
     * @param direction the direction to order by
     * @return a new {@link Order} with the given {@link Direction}.
     */
    public Order with(Direction direction) {
        return new Order(direction, this.property, this.ignoreCase, this.nullHandling);
    }

    /**
     * Returns a new {@link Order}
     * @param property must not be {@literal null} or empty.
     * @return a new {@link Order}
     * @since 1.13
     */
    public Order withProperty(String property) {
        return new Order(this.direction, property, this.ignoreCase, this.nullHandling);
    }

    /**
     * Returns a new {@link Sort} instance for the given properties.
     * @param properties the properties to sort by
     * @return a new {@link Sort} instance for the given properties
     */
    public Sort withProperties(String... properties) {
        return Sort.by(this.direction, properties);
    }

    /**
     * Returns a new {@link Order} with case-insensitive sorting enabled.
     * @return a new {@link Order} with case-insensitive sorting enabled
     */
    public Order ignoreCase() {
        return new Order(direction, property, true, nullHandling);
    }

    /**
     * Returns a {@link Order} with the given {@link NullHandling}.
     * @param nullHandling can be {@literal null}.
     * @return a {@link Order} with the given {@link NullHandling}.
     */
    public Order with(NullHandling nullHandling) {
        return new Order(direction, this.property, ignoreCase, nullHandling);
    }

    /**
     * @return a {@link Order} with {@link NullHandling#NULLS_FIRST} as null handling hint.
     */
    public Order nullsFirst() {
        return with(NullHandling.NULLS_FIRST);
    }

    /**
     * @return a {@link Order} with {@link NullHandling#NULLS_LAST} as null handling hint.
     */
    public Order nullsLast() {
        return with(NullHandling.NULLS_LAST);
    }

    /**
     * @return a {@link Order} with {@link NullHandling#NATIVE} as null handling hint.
     */
    public Order nullsNative() {
        return with(NullHandling.NATIVE);
    }

    /**
     * @return the used {@link NullHandling} hint, which can but may not be respected by the used datastore.
     */
    public NullHandling getNullHandling() {
        return nullHandling;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {

        int result = 17;

        result = 31 * result + direction.hashCode();
        result = 31 * result + property.hashCode();
        result = 31 * result + (ignoreCase ? 1 : 0);
        result = 31 * result + nullHandling.hashCode();

        return result;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(@Nullable Object obj) {

        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Order)) {
            return false;
        }

        Order that = (Order) obj;

        return this.direction.equals(that.direction) && this.property.equals(that.property)
                && this.ignoreCase == that.ignoreCase && this.nullHandling.equals(that.nullHandling);
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {

        String result = String.format("%s: %s", property, direction);

        if (!NullHandling.NATIVE.equals(nullHandling)) {
            result += ", " + nullHandling;
        }

        if (ignoreCase) {
            result += ", ignoring case";
        }

        return result;
    }
}
