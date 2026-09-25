import type { Watched } from './Watched'
import type { ReconcileState } from './ReconcileState'

/**
 * A watched record that also carries intent: what it should be, beside what its authority last
 * reported it is, so a worker can converge the world to it whenever the two differ.
 */
export interface Reconcilable<S> extends Watched {

    state: ReconcileState<S>
}
