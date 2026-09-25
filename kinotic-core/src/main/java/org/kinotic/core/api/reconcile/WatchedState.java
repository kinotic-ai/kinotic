package org.kinotic.core.api.reconcile;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * What the platform keeps on a {@link Watched} record beside the fields the record's own authority
 * writes: what it inferred, what the record belongs to, and whether its last write has been seen.
 * Every field here has one writer, and every write to it is one shard operation on the record.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class WatchedState {

    /**
     * What a watcher has inferred about the record, at most one entry per type. Empty when nothing
     * stands beside the authority's word.
     */
    private List<StatusCondition> conditions = new ArrayList<>();

    /**
     * The record this one belongs to, null for a record the platform made on its own.
     */
    private WatchedParent parent;

    /**
     * True from the record's last write until the reconcile master has acted on it.
     */
    private boolean dirty;

    /**
     * When {@link #isDirty()} was last set, epoch milliseconds, so the master clears only the write
     * it saw.
     */
    private long dirtyAt;
}
