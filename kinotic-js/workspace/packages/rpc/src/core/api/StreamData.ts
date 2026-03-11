

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
