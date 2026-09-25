package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * What the platform keeps on a {@link Reconcilable} record: everything a {@link WatchedState}
 * holds, and the intent and observation it is reconciled between. {@code desired} and
 * {@code observed} are the same shape, so the record is in its desired state exactly when they
 * are equal, the authority has seen the latest intent, nothing stands beside its word, and no
 * deletion is pending; {@link #isReconciled()} stores that answer for every write.
 *
 * @param <S> the record's state, the same shape on both sides
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ReconcileState<S> extends WatchedState {

    /**
     * What the record should be, written by whoever holds the intent.
     */
    private S desired;

    /**
     * What the record's authority last reported it is.
     */
    private S observed;

    /**
     * Counts the writes to {@link #getDesired()}, so a gap from {@link #getObservedGeneration()}
     * is what "the authority has not seen the latest intent" looks like.
     */
    private long generation;

    /**
     * When {@link #getDesired()} was last written, as epoch milliseconds: what a worker counts its
     * retries from, so a new intent starts them over.
     */
    private long desiredAt;

    /**
     * The {@link #getGeneration()} the authority had processed when it last reported.
     */
    private long observedGeneration;

    /**
     * When the record's deletion was asked for, null while it was not. Deletion is intent: a worker
     * finalizes it.
     */
    private Date deletionRequested;

    /**
     * Whether the record is in its desired state, stored so a query can ask for every record that is
     * not.
     */
    private boolean reconciled;
}
