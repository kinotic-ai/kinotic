

package org.kinotic.orchestrator.internal.api.grind;

import org.kinotic.orchestrator.api.grind.JobContext;

/**
 *
 * Created by Navid Mitchell on 7/7/20
 */
public class NoopTask<T> extends AbstractTask<T> {

    public NoopTask() {
    }

    public NoopTask(String description) {
        super(description);
    }

    @Override
    public T execute(JobContext context) throws Exception {
        return null;
    }
}
