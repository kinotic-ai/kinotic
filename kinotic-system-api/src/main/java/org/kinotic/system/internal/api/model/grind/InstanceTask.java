

package org.kinotic.system.internal.api.model.grind;

import org.kinotic.system.api.model.grind.JobContext;
import org.kinotic.system.api.model.grind.Task;

import java.util.function.Function;

/**
 * Generic {@link Task} that will "autowire" the instance prior to calling the provided invoker function
 *
 * Created by Navid Mitchell on 3/19/20
 */
public class InstanceTask<T, R> extends AbstractTask<R> {

    private final T instance;
    private final Function<T, R> invokerFunction;

    public InstanceTask(T instance, Function<T, R> invokerFunction) {
        this.instance = instance;
        this.invokerFunction = invokerFunction;
    }

    public InstanceTask(String description,
                        T instance,
                        Function<T, R> invokerFunction) {
        super(description);
        this.instance = instance;
        this.invokerFunction = invokerFunction;
    }

    @Override
    public R execute(JobContext context) throws Exception {
        context.autowire(instance);
        return invokerFunction.apply(instance);
    }
}
