package org.kinotic.grindv2.api.annotations;

import org.kinotic.grindv2.api.model.StoreType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of a steps class as one step of a job. The class is compiled to a
 * {@link JobDefinition} with {@link JobDefinition#fromSteps(Class)}: steps execute in
 * {@link #order()}, the method's parameters are injected from the job scope by type, and its
 * return value is stored back into the scope under {@link #store()}.
 *
 * A method may return the value directly, a {@code Future}/{@code CompletionStage} of it, or a
 * {@link JobDefinition} to expand dynamically discovered steps at runtime.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Step {

    /**
     * The position of this step within the class's sequence. Orders must be unique per class;
     * gaps are allowed.
     */
    int order();

    /**
     * The step description shown in run records. Defaults to the method name.
     */
    String value() default "";

    /**
     * How the step's return value is kept. Defaults to {@link StoreType#RESULT} for methods
     * that produce a value, so later steps can inject it.
     */
    StoreType store() default StoreType.RESULT;

}
