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

import {FindAllIterablePage} from '@/internal/core/api/crud/FindAllIterablePage'
import {SearchIterablePage} from '@/internal/core/api/crud/SearchIterablePage'
import { ICrudServiceProxy } from './ICrudServiceProxy'
import { IServiceProxy } from '../IServiceRegistry'
import {Identifiable, IterablePage} from '@/index'
import { Page } from './Page'
import { Pageable } from './Pageable'

export class CrudServiceProxy<T extends Identifiable<string>> implements ICrudServiceProxy<T> {

    protected serviceProxy: IServiceProxy

    constructor(serviceProxy: IServiceProxy) {
        this.serviceProxy = serviceProxy
    }

    public count(): Promise<number> {
        return this.serviceProxy.invoke('count')
    }

    public create(entity: T): Promise<T> {
        return this.serviceProxy.invoke('create', [entity])
    }

    public deleteById(id: string): Promise<void> {
        return this.serviceProxy.invoke('deleteById', [id])
    }

    public async findAll(pageable: Pageable): Promise<IterablePage<T>> {
        const page = await this.findAllSinglePage(pageable)
        return new FindAllIterablePage(pageable, page, this)
    }

    public findAllSinglePage(pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('findAll', [pageable])
    }

    public findById(id: string): Promise<T> {
        return this.serviceProxy.invoke('findById', [id])
    }

    public save(entity: T): Promise<T> {
        return this.serviceProxy.invoke('save', [entity])
    }

    public findByIdNotIn(ids: string[], page: Pageable): Promise<Page<Identifiable<string>>> {
        return (this.serviceProxy as IServiceProxy).invoke('findByIdNotIn', [ids, page])
    }

    public async search(searchText: string, pageable: Pageable): Promise<IterablePage<T>> {
        const page = await this.searchSinglePage(searchText, pageable)
        return new SearchIterablePage(pageable, page, searchText, this)
    }

    public searchSinglePage(searchText: string, pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('search', [searchText, pageable])
    }
}
