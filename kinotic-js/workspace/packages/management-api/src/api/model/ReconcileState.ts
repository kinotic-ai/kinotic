import { WatchedState } from './WatchedState'

/**
 * What the platform keeps on a reconcilable record: everything a WatchedState holds, and the intent
 * and observation it is reconciled between. desired and observed are the same shape, so the record
 * is in its desired state exactly when they are equal, the authority has seen the latest intent,
 * nothing stands beside its word, and no deletion is pending; reconciled stores that answer.
 */
export class ReconcileState<S> extends WatchedState {

    /** What the record should be, written by whoever holds the intent. */
    public desired: S | null = null

    /** What the record's authority last reported it is. */
    public observed: S | null = null

    /** Counts the writes to desired, so a gap from observedGeneration is intent the authority has not seen. */
    public generation: number = 0

    /** The generation the authority had processed when it last reported. */
    public observedGeneration: number = 0

    /** When the record's deletion was asked for, epoch milliseconds, or null while it was not. */
    public deletionRequested: number | null = null

    /** Whether the record is in its desired state. */
    public reconciled: boolean = false
}
