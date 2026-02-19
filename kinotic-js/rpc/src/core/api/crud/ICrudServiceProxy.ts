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

import {Identifiable, IterablePage} from '@/index'
import {Page} from './Page'
import {Pageable} from './Pageable'
import {IEditableDataSource} from "./IDataSource";

/**
 * A {@link ICrudServiceProxy} is a proxy for a remote CRUD service
 */
export interface ICrudServiceProxy<T extends Identifiable<string>> extends IEditableDataSource<T>{

    /**
     * Creates a new entity if one does not already exist for the given id
     * @param entity to create if one does not already exist
     * @return a {@link Promise} containing the new entity or an error if an exception occurred
     */
    create(entity: T): Promise<T>

    /**
     * Saves a given entity. Use the returned instance for further operations as the save operation might have changed the
     * entity instance completely.
     *
     * @param entity must not be {@literal null}.
     * @return a {@link Promise} emitting the saved entity.
     * @throws Error in case the given {@literal entity} is {@literal null}.
     */
    save(entity: T): Promise<T>

    /**
     * Retrieves an entity by its id.
     *
     * @param id must not be {@literal null}.
     * @return a {@link Promise} emitting the entity with the given id or {@link Promise#empty()} if none found.
     * @throws IllegalArgumentException in case the given {@literal identity} is {@literal null}.
     */
    findById(id: string): Promise<T>

    /**s
     * Returns the number of entities available.
     *
     * @return a {@link Promise} emitting the number of entities.
     */
    count(): Promise<number>

    /**
     * Deletes the entity with the given id.
     *
     * @param id must not be {@literal null}.
     * @return a {@link Promise} signaling when operation has completed.
     * @throws IllegalArgumentException in case the given {@literal identity} is {@literal null}.
     */
    deleteById(id: string): Promise<void>

    /**
     * Returns a {@link Page} of entities meeting the paging restriction provided in the {@code Pageable} object.
     *
     * @param pageable the page settings to be used
     * @return a {@link Promise} emitting the page of entities
     */
    findAll(pageable: Pageable): Promise<IterablePage<T>>

    /**
     * Returns a {@link Page} of entities not in the ids list and meeting the paging restriction provided in the {@code Pageable} object.
     *
     * @param ids not to be returned in the Page
     * @param pageable the page settings to be used
     * @return a {@link Promise} emitting the page of entities
     */
    findByIdNotIn(ids: string[], pageable: Pageable): Promise<Page<Identifiable<string>>>

    /**
     * Returns a {@link Page} of entities matching the search text and paging restriction provided in the {@code Pageable} object.
     *
     * @param searchText the text to search for entities for
     * @param pageable the page settings to be used
     * @return a {@link Promise} emitting the page of entities
     */
    search(searchText: string, pageable: Pageable): Promise<IterablePage<T>>

}
