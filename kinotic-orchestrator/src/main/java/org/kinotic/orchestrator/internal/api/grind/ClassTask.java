

package org.kinotic.orchestrator.internal.api.grind;

import org.kinotic.orchestrator.api.grind.JobContext;
import org.kinotic.orchestrator.api.grind.Task;

import java.util.concurrent.Callable;

/**
 * An interesting type of {@link Task} that constructs an instance of the given class with full
 * injection each execution, then invokes it for the result
 *
 * Created by Navid Mitchell on 3/19/20
 */
public class ClassTask<R> extends AbstractTask<R> {

    private final Class<? extends Callable<R>> clazz;

    public ClassTask(String description,
                     Class<? extends Callable<R>> clazz) {
        super(description != null ? description : clazz.getSimpleName());
        this.clazz = clazz;
    }

    @Override
    public R execute(JobContext context) throws Exception {
        return context.instantiate(clazz).call();
    }

}
