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

import { Identifiable } from '@/index'

export enum StreamOperation {
    EXISTING = 'EXISTING',
    UPDATE = 'UPDATE',
    REMOVE = 'REMOVE'
}

/**
 * Holder for domain objects that will be returned as a stream of changes to a data set
 *
 * Created by Navid Mitchell on 6/3/20
 */
export class StreamData<I, T> implements Identifiable<I> {

    public streamOperation: StreamOperation

    public id: I

    public value: T

    constructor(streamOperation: StreamOperation, id: I, value: T) {
        this.streamOperation = streamOperation
        this.id = id
        this.value = value
    }

    public isSet(): boolean {
        return this.value !== null && this.value !== undefined
    }

}
