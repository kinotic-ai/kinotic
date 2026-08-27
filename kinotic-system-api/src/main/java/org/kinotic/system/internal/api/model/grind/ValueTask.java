

package org.kinotic.system.internal.api.model.grind;

import org.kinotic.system.api.model.grind.JobContext;
import org.kinotic.system.api.model.grind.Task;

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
    public R execute(JobContext context) {
        return value;
    }
}
