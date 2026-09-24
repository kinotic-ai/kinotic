package org.kinotic.core.api.reconcile;

import org.apache.commons.lang3.Validate;

import java.time.Duration;

/**
 * What a {@link Reconciler} asks of the reconcile master once it has acted: nothing until the record
 * changes again, another call at once, or another call after a delay.
 *
 * @param after how long to wait before the next call, zero for at once, null for no call
 */
public record Requeue(Duration after) {

    /**
     * The record needs nothing more until it changes.
     */
    public static final Requeue NONE = new Requeue(null);

    /**
     * Call again at once.
     */
    public static final Requeue NOW = new Requeue(Duration.ZERO);

    /**
     * @param delay how long to wait before the next call
     * @return a request for another call after the delay
     */
    public static Requeue after(Duration delay) {
        Validate.notNull(delay, "delay cannot be null");
        return new Requeue(delay);
    }

    /**
     * @return true when another call is asked for
     */
    public boolean wanted() {
        return after != null;
    }
}
