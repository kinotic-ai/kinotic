package org.kinotic.grind.api.model;

import org.apache.commons.lang3.Validate;
import org.kinotic.grind.internal.api.model.DefaultTask;
import org.kinotic.grind.internal.api.model.TaskInstanceFactory;

import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Static factories for the common {@link Task} shapes, so a single task does not require a
 * class of its own. Each takes what every task needs, its description and its work; the rest is
 * set with the returned task's fluent methods.
 */
public class Tasks {

    /**
     * Creates a {@link Task} that constructs a new instance of the given class on every
     * execution, with full injection against the job scope, then invokes
     * {@link Callable#call()} for the result. The task description is the class's simple name.
     * @param taskClass the class to construct and invoke
     * @param <R> the result type
     * @return the task
     */
    public static <R> Task<R> fromClass(Class<? extends Callable<R>> taskClass) {
        return fromClass(taskClass.getSimpleName(), taskClass);
    }

    /**
     * Creates a {@link Task} that constructs a new instance of the given class on every
     * execution, with full injection against the job scope, then invokes
     * {@link Callable#call()} for the result.
     * @param description of the task
     * @param taskClass the class to construct and invoke
     * @param <R> the result type
     * @return the task
     */
    public static <R> Task<R> fromClass(String description, Class<? extends Callable<R>> taskClass) {
        Validate.notNull(taskClass, "taskClass cannot be null");
        return DefaultTask.of(description, context -> context.instantiate(taskClass).call());
    }

    /**
     * Creates a {@link Task} that invokes {@link Callable#call()} for the result. A callable the
     * job scope injects, through an {@code @Autowired} or {@code @Value} field, an injected method
     * or a lifecycle callback, written inline as an anonymous class, is the pattern of the task:
     * every execution invokes an instance of its own, a new instance of that class holding what
     * the given one captured, injected against the job scope. Any other callable, such as a lambda
     * or a method reference, is invoked by every execution.
     * @param description of the task
     * @param callable to invoke
     * @param <R> the result type
     * @return the task
     * @throws IllegalArgumentException if the job scope injects the callable and it is an instance
     *         of a named class, which {@link #fromClass(String, Class)} takes
     */
    public static <R> Task<R> fromCallable(String description, Callable<R> callable) {
        Validate.notNull(callable, "callable cannot be null");
        TaskInstanceFactory<Callable<R>> instances = TaskInstanceFactory.of(callable);
        return DefaultTask.of(description, context -> instances.instanceFor(context).call());
    }

    /**
     * Creates a {@link Task} that invokes {@link Supplier#get()} for the result. A supplier the
     * job scope injects is given to every execution as an instance of its own, as
     * {@link #fromCallable(String, Callable)} describes.
     * @param description of the task
     * @param supplier to invoke
     * @param <R> the result type
     * @return the task
     * @throws IllegalArgumentException as {@link #fromCallable(String, Callable)} describes
     */
    public static <R> Task<R> fromSupplier(String description, Supplier<R> supplier) {
        Validate.notNull(supplier, "supplier cannot be null");
        TaskInstanceFactory<Supplier<R>> instances = TaskInstanceFactory.of(supplier);
        return DefaultTask.of(description, context -> instances.instanceFor(context).get());
    }

    /**
     * Creates a {@link Task} that invokes {@link Runnable#run()}. A runnable the job scope
     * injects is given to every execution as an instance of its own, as
     * {@link #fromCallable(String, Callable)} describes.
     * @param description of the task
     * @param runnable to invoke
     * @return the task
     * @throws IllegalArgumentException as {@link #fromCallable(String, Callable)} describes
     */
    public static Task<Void> fromRunnable(String description, Runnable runnable) {
        Validate.notNull(runnable, "runnable cannot be null");
        TaskInstanceFactory<Runnable> instances = TaskInstanceFactory.of(runnable);
        return DefaultTask.of(description, context -> {
            instances.instanceFor(context).run();
            return null;
        });
    }

    /**
     * Creates a {@link Task} that passes the given value straight through without invocation.
     * @param description of the task
     * @param value the task's result
     * @param <R> the result type
     * @return the task
     */
    public static <R> Task<R> fromValue(String description, R value) {
        return DefaultTask.of(description, context -> value);
    }

    /**
     * Creates a {@link Task} that does nothing, so a dynamic task can decline to add work.
     * @param description of why the task is a noop
     * @param <R> the result type
     * @return the noop task
     */
    public static <R> Task<R> noop(String description) {
        return fromValue(description, null);
    }

    /**
     * Creates a {@link Task} applying the given transformation to another task's result. The new
     * task has the other task's description and icon; its store and views, which concern the
     * transformed value, are declared on the returned task.
     * @param from the task producing the value
     * @param transformer applied to the produced value
     * @param <T> the produced type
     * @param <R> the transformed type
     * @return the task
     */
    public static <T, R> Task<R> transformResult(Task<T> from, Function<T, R> transformer) {
        return DefaultTask.<R>of(from.getDescription(), context -> transformer.apply(from.execute(context)))
                          .icon(from.getIcon());
    }

}
