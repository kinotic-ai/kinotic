package org.kinotic.queue.internal.cluster;

import java.util.concurrent.TimeUnit;

/**
 * Measures how much time passed on this node. A reading is the epoch milliseconds the wall clock showed when this
 * class loaded, advanced since by {@link System#nanoTime()}, so setting the wall clock never moves it. A reading only
 * compares with another reading taken in the same process.
 */
public final class MonotonicClock {

    private static final long ORIGIN_MILLIS = System.currentTimeMillis();
    private static final long ORIGIN_NANOS = System.nanoTime();

    private MonotonicClock() {
    }

    /**
     * @return the current reading, in milliseconds
     */
    public static long millis() {
        return ORIGIN_MILLIS + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ORIGIN_NANOS);
    }
}
