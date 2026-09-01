package org.kinotic.grind.api.annotations;

import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.StoreType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of a tasks class as one task of a job. The class is compiled to a
 * {@link JobDefinition} with {@link JobDefinition#fromTasks(Class)}: tasks execute in
 * {@link #order()}, the method's parameters are injected from the job scope by type, and its
 * return value is stored back into the scope under {@link #store()}.
 *
 * A method may return the value directly, a {@code Future}/{@code CompletionStage} of it, or a
 * {@link JobDefinition} to expand dynamically discovered tasks at runtime.
 *
 * See {@link org.kinotic.grind.api.model.Task#execute} for how a task body must handle
 * asynchronous results.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Task {

    /**
     * The position of this task within the class's sequence. Orders must be unique per class;
     * gaps are allowed.
     */
    int order();

    /**
     * The task description shown in run records. Defaults to the method name.
     */
    String value() default "";

    /**
     * How the task's return value is kept. Defaults to {@link StoreType#RESULT} for methods
     * that produce a value, so later tasks can inject it.
     */
    StoreType store() default StoreType.RESULT;

    /**
     * True to publish the task's return value to watchers of the run, serialized as JSON onto
     * the run's {@code TaskCompletedEvent}. The value must be JSON-serializable.
     */
    boolean wire() default false;

}
