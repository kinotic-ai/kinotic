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

import {Identifiable} from '@/api/Identifiable'
import {AbstractIterablePage} from '@/core/api/crud/AbstractIterablePage'
import {CrudServiceProxy} from '@/core/api/crud/CrudServiceProxy'
import {Page} from '@/core/api/crud/Page'
import {Pageable} from '@/core/api/crud/Pageable'

/**
 * {@link IterablePage} for use when finding all
 */
export class FindAllIterablePage<T extends Identifiable<string>> extends AbstractIterablePage<T> {

    private readonly crudServiceProxy: CrudServiceProxy<T>

    constructor(pageable: Pageable,
                page: Page<T>,
                crudServiceProxy: CrudServiceProxy<T>) {
        super(pageable, page)
        this.crudServiceProxy = crudServiceProxy
    }

    protected findNext(pageable: Pageable): Promise<Page<T>> {
        return this.crudServiceProxy.findAllSinglePage(pageable)
    }

}
