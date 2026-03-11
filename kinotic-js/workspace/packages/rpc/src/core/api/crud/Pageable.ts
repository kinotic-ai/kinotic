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

import {Sort} from './Sort'

/**
 * Abstract interface for pagination information.
 *
 * Adapted from the Spring Data Commons Package
 *
 * @author Oliver Gierke
 * @author Navid Mitchell
 */
export abstract class Pageable {

    /**
     * Returns the sorting parameters (optional).
     */
    sort?: Sort | null | undefined = null

    /**
     * Returns the number of items to be returned.
     */
    pageSize: number = 25

    /**
     * Creates a {@link Pageable} that uses Offset based pagination.
     * @param pageNumber zero based page index.
     * @param pageSize the size of the page to be returned.
     * @param sort the sorting parameters (optional).
     */
    public static create(pageNumber: number, pageSize: number, sort?: Sort | null): OffsetPageable {
        return new OffsetPageable(pageNumber, pageSize, sort)
    }

    /**
     * Creates a {@link Pageable} that uses Cursor based pagination.
     * @param cursor the cursor to be used for subsequent retrieval of data, or null if this is the first page.
     * @param pageSize the size of the page to be returned.
     * @param sort the sorting parameters (optional).
     */
    public static createWithCursor(cursor: string | null, pageSize: number, sort?: Sort | null): CursorPageable {
        return new CursorPageable(cursor, pageSize, sort)
    }
}

/**
 * Implementation of {@link Pageable} that uses Offset based pagination.
 */
export class OffsetPageable extends Pageable {
    /**
     * Returns the page to be returned.
     */
    pageNumber: number = 0

    /**
     * Creates a {@link Pageable} that uses Offset based pagination.
     * @param pageNumber zero based page index.
     * @param pageSize the size of the page to be returned.
     * @param sort the sorting parameters (optional).
     */
    constructor(pageNumber: number, pageSize: number, sort?: Sort | null) {
        super()
        this.pageNumber = pageNumber
        this.pageSize = pageSize
        this.sort = sort
    }
}

/**
 * Implementation of {@link Pageable} that uses Cursor based pagination.
 */
export class CursorPageable extends Pageable {
    /**
     * The cursor to be used for subsequent retrieval of data, or null if this is the first page.
     */
    cursor: string | null

    /**
     * Creates a {@link Pageable} that uses Cursor based pagination.
     * @param cursor the cursor to be used for subsequent retrieval of data, or null if this is the first page.
     * @param pageSize the size of the page to be returned.
     * @param sort the sorting parameters (optional).
     */
    constructor(cursor: string | null, pageSize: number, sort?: Sort | null) {
        super()
        this.cursor = cursor
        this.pageSize = pageSize
        this.sort = sort
    }
}
