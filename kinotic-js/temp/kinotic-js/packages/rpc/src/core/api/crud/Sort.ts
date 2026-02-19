/*
 * Copyright 2008-2019 the original author or authors.
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

/**
 * Enumeration for sort directions.
 *
 * Adapted from the Spring Data Commons Package
 *
 * @author Oliver Gierke
 * @author Navid Mitchell
 */
export enum Direction {
    ASC = 'ASC',
    DESC = 'DESC'
}

/**
 * Enumeration for null handling hints that can be used in {@link Order} expressions.
 *
 * Adapted from the Spring Data Commons Package
 *
 * @author Thomas Darimont
 * @author Navid Mitchell
 * @since 1.8
 */
export enum NullHandling {

    /**
     * Lets the data store decide what to do with nulls.
     */
    NATIVE = 'NATIVE',

    /**
     * A hint to the used data store to order entries with null values before non-null entries.
     */
    NULLS_FIRST = 'NULLS_FIRST',

    /**
     * A hint to the used data store to order entries with null values after non-null entries.
     */
    NULLS_LAST = 'NULLS_LAST'
}

export class Order {
    public property: string
    public direction: Direction = Direction.ASC
    public nullHandling: NullHandling = NullHandling.NATIVE

    constructor(property: string, direction: Direction | null) {
        this.property = property
        if (direction !== null) {
           this.direction = direction
        }
    }

    /**
     * Returns whether sorting for this property shall be ascending.
     */
    public isAscending(): boolean {
        return this.direction === Direction.ASC
    }

    /**
     * Returns whether sorting for this property shall be descending.
     */
    public isDescending(): boolean {
        return this.direction === Direction.DESC
    }

}

/**
 * Sort option for queries. You have to provide at least a list of properties to sort for that must not include
 * {@literal null} or empty strings. The direction defaults to {@link Sort#DEFAULT_DIRECTION}.
 *
 * Adapted from the Spring Data Commons Package
 *
 * @author Oliver Gierke
 * @author Thomas Darimont
 * @author Mark Paluch
 * @author Navid Mitchell
 */
export class Sort {

    public orders: Order[] = []

}
