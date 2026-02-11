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
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Sort option for queries. You have to provide at least a list of properties to sort for that must not include
 * {@literal null} or empty strings. The direction defaults to {@link Sort#DEFAULT_DIRECTION}.
 *
 * @author Oliver Gierke
 * @author Thomas Darimont
 * @author Mark Paluch
 * @author Johannes Englmeier
 */
public class Sort implements Iterable<Order>{
    private static final Sort UNSORTED = Sort.by(new Order[0]);

    public static final Direction DEFAULT_DIRECTION = Direction.ASC;

    private final List<Order> orders;

    protected Sort(List<Order> orders) {
        this.orders = orders;
    }

    /**
     * Creates a new {@link Sort} instance.
     *
     * @param direction defaults to {@link Sort#DEFAULT_DIRECTION} (for {@literal null} cases, too)
     * @param properties must not be {@literal null} or contain {@literal null} or empty strings.
     */
    private Sort(Direction direction, List<String> properties) {

        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("You have to provide at least one property to sort by");
        }

        this.orders = properties.stream() //
                                .map(it -> new Order(direction, it)) //
                                .collect(Collectors.toList());
    }

    /**
     * Creates a new {@link Sort} for the given properties.
     *
     * @param properties must not be {@literal null}.
     * @return a new {@link Sort} for the given properties.
     */
    public static Sort by(String... properties) {

        Assert.notNull(properties, "Properties must not be null");

        return properties.length == 0 //
                ? Sort.unsorted() //
                : new Sort(DEFAULT_DIRECTION, Arrays.asList(properties));
    }

    /**
     * Creates a new {@link Sort} for the given {@link Order}s.
     *
     * @param orders must not be {@literal null}.
     * @return a new {@link Sort} for the given {@link Order}s.
     */
    public static Sort by(List<Order> orders) {

        Assert.notNull(orders, "Orders must not be null");

        return orders.isEmpty() ? Sort.unsorted() : new Sort(orders);
    }

    /**
     * Creates a new {@link Sort} for the given {@link Order}s.
     *
     * @param orders must not be {@literal null}.
     * @return a new {@link Sort} for the given {@link Order}s.
     */
    public static Sort by(Order... orders) {

        Assert.notNull(orders, "Orders must not be null");

        return new Sort(Arrays.asList(orders));
    }

    /**
     * Creates a new {@link Sort} for the given {@link Order}s.
     *
     * @param direction must not be {@literal null}.
     * @param properties must not be {@literal null}.
     * @return a new {@link Sort} for the given direction and properties.
     */
    public static Sort by(Direction direction, String... properties) {

        Assert.notNull(direction, "Direction must not be null");
        Assert.notNull(properties, "Properties must not be null");
        Assert.isTrue(properties.length > 0, "At least one property must be given");

        return Sort.by(Arrays.stream(properties)//
                                                             .map(it -> new Order(direction, it))//
                                                             .collect(Collectors.toList()));
    }

    /**
     * Returns a {@link Sort} instances representing no sorting setup at all.
     * @return a {@link Sort} instance representing no sorting setup at all.
     */
    public static Sort unsorted() {
        return UNSORTED;
    }

    /**
     * Returns a new {@link Sort} with the current setup but descending order direction.
     * @return a new {@link Sort} with reversed order direction.
     */
    public Sort descending() {
        return withDirection(Direction.DESC);
    }

    /**
     * Returns a new {@link Sort} with the current setup but ascending order direction.
     *
     * @return a new {@link Sort} with the current setup but ascending order direction.
     */
    public Sort ascending() {
        return withDirection(Direction.ASC);
    }

    public boolean isSorted() {
        return !orders.isEmpty();
    }

    public boolean isUnsorted() {
        return !isSorted();
    }

    /**
     * Returns a new {@link Sort} consisting of the {@link Order}s of the current {@link Sort} combined with the given
     * ones.
     *
     * @param sort must not be {@literal null}.
     * @return a new {@link Sort} combining the {@link Order}s of the current {@link Sort} with the given ones.
     */
    public Sort and(Sort sort) {

        Assert.notNull(sort, "Sort must not be null");

        ArrayList<Order> these = new ArrayList<>(orders);

        these.addAll(sort.orders);

        return Sort.by(these);
    }

    /**
     * Returns the order registered for the given property.
     *
     * @param property must not be {@literal null}.
     * @return the order registered for the given property or null if none registered.
     */
    @Nullable
    public Order getOrderFor(String property) {

        for (Order order : orders) {
            if (order.getProperty().equals(property)) {
                return order;
            }
        }

        return null;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Iterable#iterator()
     */
    public Iterator<Order> iterator() {
        return this.orders.iterator();
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

        if (!(obj instanceof Sort)) {
            return false;
        }

        Sort that = (Sort) obj;

        return orders.equals(that.orders);
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {

        int result = 17;
        result = 31 * result + orders.hashCode();
        return result;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return orders.isEmpty() ? "UNSORTED" : StringUtils.collectionToCommaDelimitedString(orders);
    }

    /**
     * Creates a new {@link Sort} with the current setup but the given order direction.
     *
     * @param direction must not be {@literal null}.
     * @return a new {@link Sort} with the current setup but the given order direction.
     */
    private Sort withDirection(Direction direction) {
        return Sort.by(orders.stream().map(it -> it.with(direction)).collect(Collectors.toList()));
    }
}

