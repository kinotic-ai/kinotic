import type { Identifiable } from '../crud/Identifiable'
import type { WatchedState } from './WatchedState'

/**
 * A record with more than one writer, each owning its own fields, whose changes the platform watches:
 * it infers conditions beside the authority's word, records every write, and tells the record's
 * parent when it changes.
 */
export interface Watched extends Identifiable<string> {

    /** What the platform keeps on this record beside the authority's fields. */
    state: WatchedState
}
