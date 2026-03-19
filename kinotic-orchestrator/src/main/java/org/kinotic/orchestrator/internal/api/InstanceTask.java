

package org.kinotic.orchestrator.internal.api;

import org.kinotic.orchestrator.api.Task;
import org.springframework.context.support.GenericApplicationContext;

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
    public R execute(GenericApplicationContext applicationContext) throws Exception {
        applicationContext.getAutowireCapableBeanFactory().autowireBean(instance);
        applicationContext.getAutowireCapableBeanFactory().initializeBean(instance,"");
        return invokerFunction.apply(instance);
    }
}
