import type { StatusCondition } from './StatusCondition'
import type { WatchedParent } from './WatchedParent'

/**
 * What the platform keeps on a watched record beside the fields the record's own authority writes:
 * what it inferred, what the record belongs to, and whether its last write has been seen.
 */
export class WatchedState {

    /**
     * What a watcher has inferred about the record, at most one entry per type. Empty when nothing
     * stands beside the authority's word.
     */
    public conditions: StatusCondition[] = []

    /**
     * The record this one belongs to, null for a record the platform made on its own.
     */
    public parent: WatchedParent | null = null

    /**
     * True from the record's last write until the reconcile master has acted on it.
     */
    public dirty: boolean = false

    /**
     * When dirty was last set, epoch milliseconds.
     */
    public dirtyAt: number = 0
}
