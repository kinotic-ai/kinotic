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

import org.apache.commons.lang3.Validate;
import org.kinotic.continuum.api.Identifiable;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * Extends {@link CrudService} to add a support for types that are {@link Identifiable}
 * Created by navid on 2/3/20
 */
public interface IdentifiableCrudService<T extends Identifiable<ID>, ID> extends CrudService<T, ID> {

    /**
     * Creates a new entity if one does not already exist for the given id
     * @param entity to create if one does not already exist
     * @return a {@link Mono} containing the new entity or an error if an exception occurred
     */
    default CompletableFuture<T> create(T entity) {
        Validate.notNull(entity, "Entity cannot be null");
        ID id = entity.getId();
        if(id != null){
            return findById(entity.getId())
                    .thenCompose(result -> {
                        if (result == null) {
                            return save(entity);
                        } else {
                            CompletableFuture<T> exceptionFuture = new CompletableFuture<>();
                            exceptionFuture.completeExceptionally(new IllegalArgumentException(entity.getClass().getSimpleName() + " for the id " + entity.getId() + " already exists"));
                            return exceptionFuture;
                        }
                    });
        }else{
            return save(entity);
        }
    }


}
