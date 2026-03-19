

package org.kinotic.orchestrator.internal.api;

import org.springframework.context.support.GenericApplicationContext;

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
    public T execute(GenericApplicationContext applicationContext) throws Exception {
        return null;
    }
}
