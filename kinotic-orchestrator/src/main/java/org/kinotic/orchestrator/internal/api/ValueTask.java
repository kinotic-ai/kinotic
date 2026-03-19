

package org.kinotic.orchestrator.internal.api;

import org.kinotic.orchestrator.api.Task;
import org.springframework.context.support.GenericApplicationContext;

/**
 * A {@link Task} that just passes the provided value straight through without any autowiring or invocation
 *
 * Created by Navid Mitchell on 3/19/20
 */
public class ValueTask<R> extends AbstractTask<R> {

    private final R value;

    public ValueTask(R value) {
        this.value = value;
    }

    public ValueTask(String description, R value) {
        super(description);
        this.value = value;
    }

    @Override
    public R execute(GenericApplicationContext applicationContext) {
        return value;
    }
}
