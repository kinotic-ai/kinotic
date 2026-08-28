package org.kinotic.grindv2.api.model;

import org.apache.commons.lang3.Validate;

import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Static factories for the common {@link Task} shapes, so a single task does not require a
 * class of its own.
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
        return new Task<>() {
            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public R execute(JobContext context) throws Exception {
                return context.instantiate(taskClass).call();
            }
        };
    }

    /**
     * Creates a {@link Task} that injects the given instance's annotated members against the
     * job scope, then invokes {@link Callable#call()} for the result.
     * @param description of the task
     * @param callable to inject and invoke
     * @param <R> the result type
     * @return the task
     */
    public static <R> Task<R> fromCallable(String description, Callable<R> callable) {
        Validate.notNull(callable, "callable cannot be null");
        return new Task<>() {
            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public R execute(JobContext context) throws Exception {
                context.autowire(callable);
                return callable.call();
            }
        };
    }

    /**
     * Creates a {@link Task} that injects the given instance's annotated members against the
     * job scope, then invokes {@link Supplier#get()} for the result.
     * @param description of the task
     * @param supplier to inject and invoke
     * @param <R> the result type
     * @return the task
     */
    public static <R> Task<R> fromSupplier(String description, Supplier<R> supplier) {
        Validate.notNull(supplier, "supplier cannot be null");
        return new Task<>() {
            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public R execute(JobContext context) {
                context.autowire(supplier);
                return supplier.get();
            }
        };
    }

    /**
     * Creates a {@link Task} that injects the given instance's annotated members against the
     * job scope, then invokes {@link Runnable#run()}.
     * @param description of the task
     * @param runnable to inject and invoke
     * @return the task
     */
    public static Task<Void> fromRunnable(String description, Runnable runnable) {
        Validate.notNull(runnable, "runnable cannot be null");
        return new Task<>() {
            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public Void execute(JobContext context) {
                context.autowire(runnable);
                runnable.run();
                return null;
            }
        };
    }

    /**
     * Creates a {@link Task} that passes the given value straight through without invocation.
     * @param description of the task
     * @param value the task's result
     * @param <R> the result type
     * @return the task
     */
    public static <R> Task<R> fromValue(String description, R value) {
        return new Task<>() {
            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public R execute(JobContext context) {
                return value;
            }
        };
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
     * Creates a {@link Task} applying the given transformation to another task's result.
     * @param from the task producing the value
     * @param transformer applied to the produced value
     * @param <T> the produced type
     * @param <R> the transformed type
     * @return the task
     */
    public static <T, R> Task<R> transformResult(Task<T> from, Function<T, R> transformer) {
        return new Task<>() {
            @Override
            public String getDescription() {
                return from.getDescription();
            }

            @Override
            public R execute(JobContext context) throws Exception {
                return transformer.apply(from.execute(context));
            }
        };
    }

}
