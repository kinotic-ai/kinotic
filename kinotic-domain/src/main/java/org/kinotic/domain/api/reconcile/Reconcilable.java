package org.kinotic.domain.api.reconcile;

/**
 * A {@link Watched} record that also carries intent: what it should be, beside what its authority
 * last reported it is, so a worker can converge the world to it whenever the two differ.
 *
 * @param <S> the record's state, the same shape on both sides
 */
public interface Reconcilable<S> extends Watched {

    @Override
    ReconcileState<S> getState();
}
