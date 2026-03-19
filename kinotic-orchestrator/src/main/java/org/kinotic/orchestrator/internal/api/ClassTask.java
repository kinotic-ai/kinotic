

package org.kinotic.orchestrator.internal.api;

import org.kinotic.orchestrator.api.Task;
import org.springframework.context.support.GenericApplicationContext;

import java.util.function.Function;

/**
 * An interesting type of {@link Task} that lets you define a class to be constructed then a method invoked for the result
 *
 * Created by Navid Mitchell on 3/19/20
 */
public class ClassTask<T, R> extends AbstractTask<R> {

    private final Class<? extends T> clazz;
    private final Function<T, R> invokerFunction;

    public ClassTask(Class<? extends T> clazz,
                     Function<T, R> invokerFunction) {
        this.clazz = clazz;
        this.invokerFunction = invokerFunction;
    }

    public ClassTask(String description,
                     Class<? extends T> clazz,
                     Function<T, R> invokerFunction) {
        super(description);
        this.clazz = clazz;
        this.invokerFunction = invokerFunction;
    }

    @Override
    public R execute(GenericApplicationContext applicationContext) {
        T bean = applicationContext.getAutowireCapableBeanFactory().createBean(clazz);
        return invokerFunction.apply(bean);
    }

}
