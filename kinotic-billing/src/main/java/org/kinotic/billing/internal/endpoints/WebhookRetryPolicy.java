package org.kinotic.billing.internal.endpoints;

/**
 * Retry schedule for webhook event processing: exponential backoff starting at 2s,
 * doubling per attempt, dead-lettering after {@link #MAX_ATTEMPTS}.
 */
public class WebhookRetryPolicy {

    public static final int MAX_ATTEMPTS = 5;

    private static final long BASE_DELAY_MILLIS = 2_000;

    /**
     * @param attempt the attempt that just failed (1-based)
     * @return how long to wait before the next attempt
     */
    public long delayMillis(int attempt) {
        return BASE_DELAY_MILLIS * (1L << (attempt - 1));
    }

    /**
     * @param attempt the attempt that just failed (1-based)
     * @return true when no further attempts should be made
     */
    public boolean isExhausted(int attempt) {
        return attempt >= MAX_ATTEMPTS;
    }
}
