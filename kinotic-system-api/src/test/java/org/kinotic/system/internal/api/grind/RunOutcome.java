package org.kinotic.system.internal.api.grind;

import java.util.List;

/**
 * What a subscribed job execution produced: the VALUE results in emission order and the
 * terminal error, if any.
 */
public record RunOutcome(List<Object> values, Throwable error) {

    public boolean failed() {
        return error != null;
    }
}
