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

import { ICrudServiceProxyFactory } from './ICrudServiceProxyFactory'
import { ICrudServiceProxy } from './ICrudServiceProxy'
import { CrudServiceProxy } from './CrudServiceProxy'
import { Identifiable } from '@/index'
import { IServiceRegistry } from '@/core/api/IServiceRegistry'


/**
 * Default implementation of {@link ICrudServiceProxyFactory}
 */
export class CrudServiceProxyFactory implements ICrudServiceProxyFactory {

    private serviceRegistry: IServiceRegistry

    constructor(serviceRegistry: IServiceRegistry) {
        this.serviceRegistry = serviceRegistry
    }

    public crudServiceProxy<T extends Identifiable<string>>(serviceIdentifier: string): ICrudServiceProxy<T> {
        if ( typeof serviceIdentifier === 'undefined' || serviceIdentifier.length === 0 ) {
            throw new Error('The serviceIdentifier provided must contain a value')
        }
        return new CrudServiceProxy<T>(this.serviceRegistry.serviceProxy(serviceIdentifier))
    }

}
