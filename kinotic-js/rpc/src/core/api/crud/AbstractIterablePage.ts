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

import {IterablePage} from '@/core/api/crud/IterablePage'
import {Page} from '@/core/api/crud/Page'
import {CursorPageable, OffsetPageable, Pageable} from '@/core/api/crud/Pageable'

export abstract class AbstractIterablePage<T> implements IterablePage<T> {

    private readonly pageable: Pageable
    private currentPage: Page<T>
    private firstPage: boolean = true

    protected constructor(pageable: Pageable,
                          page: Page<T>) {
        this.pageable = pageable
        this.currentPage = page
    }

    /**
     * Finds the next page of results based on the given pageable
     * @param pageable to use to find the next page
     * @return the next page of results
     */
    protected abstract findNext(pageable: Pageable): Promise<Page<T>>;

    async next(): Promise<IteratorResult<IterablePage<T>>> {
        let ret: IteratorResult<IterablePage<T>>

        if(this.firstPage) {
            this.firstPage = false
            // Check has content in case this is an empty page
            // Such as when querying entities with no results
            ret = {done: !this.hasContent(), value: this}
        } else {
            if(this.isOffsetPageable()){

                const offsetPageable = this.pageable as OffsetPageable
                offsetPageable.pageNumber++

                // ignoring undefined should be fine since OffsetPageable should always have totalElements
                const numPages = Math.ceil(this.totalElements as number / this.pageable.pageSize)

                // Check if we are still less than the number of pages
                if(offsetPageable.pageNumber < numPages){
                    this.currentPage = await this.findNext(this.pageable)
                    ret = {done: false, value: this}
                }else{
                    ret = {done: true, value: this}
                }
            }else{

                const cursorPageable = this.pageable as CursorPageable
                cursorPageable.cursor = this.currentPage.cursor as string || null

                this.currentPage = await this.findNext(this.pageable)

                // The last page will have a null cursor, so this will work correctly
                ret = {done: this.isLastPage(), value: this}
            }
        }
        return ret
    }

    [Symbol.asyncIterator](): AsyncIterableIterator<IterablePage<T>> {
        return this
    }

    hasContent(): boolean {
        return this.currentPage.content !== null && this.currentPage.content !== undefined && this.currentPage.content.length > 0
    }

    isLastPage(): boolean {
        let ret: boolean
        if (this.isOffsetPageable()) {
            // ignoring undefined should be fine since OffsetPageable should always have totalElements
            const numPages = Math.ceil(this.totalElements as number / this.pageable.pageSize)
            ret = numPages === (this.pageable as OffsetPageable).pageNumber + 1
        }else{
            ret = !this.firstPage && this.currentPage.cursor === null
        }
        return ret
    }

    private isOffsetPageable(): boolean {
        return (this.pageable as OffsetPageable).pageNumber !== undefined
    }

    get totalElements(): number | null | undefined {
        return this.currentPage.totalElements
    }

    get cursor(): string | null | undefined {
        return this.currentPage.cursor
    }

    get content(): T[] | null | undefined {
        return this.currentPage.content
    }

}
