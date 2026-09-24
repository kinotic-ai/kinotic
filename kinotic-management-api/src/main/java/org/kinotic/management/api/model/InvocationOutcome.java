package org.kinotic.management.api.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * How an invocation a client made through the gateway ended.
 */
@Getter
@RequiredArgsConstructor
public enum InvocationOutcome {

    /** The service replied with a value, or completed the stream it returned. */
    OK(false),

    /** The service replied with an error. */
    ERROR(true),

    /** No service took the invocation, or the node serving it left the cluster before it replied. */
    UNAVAILABLE(true),

    /** The client stopped waiting: it cancelled the invocation or closed its connection. */
    CANCELLED(false);

    /** Whether the invocation counts as failed. */
    private final boolean failure;

    /**
     * @return the value of the {@link InvocationMetrics#OUTCOME} attribute an invocation that ended
     *         this way is recorded with
     */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
