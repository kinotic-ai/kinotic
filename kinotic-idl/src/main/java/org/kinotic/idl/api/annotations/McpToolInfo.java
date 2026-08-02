package org.kinotic.idl.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes a function as a Model Context Protocol tool without exposing it as one. A base interface such as
 * a CRUD contract gives each of its functions a title, a description, and behavior hints once, and every
 * service that exposes them with {@link McpTool} serves what it wrote.
 *
 * An {@code @McpTool} on the same function wins over this. What neither sets comes from the function itself:
 * its Javadoc becomes the description, and its name becomes its half of the title and its hints.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpToolInfo {

    /**
     * What the tool does, in the words an LLM reads to decide whether to call it.
     */
    String description() default "";

    /**
     * The function's half of the tool's display title. The service's half always comes first.
     */
    String title() default "";

    /**
     * Indicates the tool does not modify its environment.
     */
    boolean readOnlyHint() default false;

    /**
     * Indicates the tool may perform destructive updates (only meaningful when not read-only).
     */
    boolean destructiveHint() default false;

    /**
     * Indicates repeated calls with the same arguments have no additional effect.
     */
    boolean idempotentHint() default false;

}
