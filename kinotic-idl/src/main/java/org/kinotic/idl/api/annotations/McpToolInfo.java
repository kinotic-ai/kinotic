package org.kinotic.idl.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the Model Context Protocol metadata a function serves once some other declaration exposes it as a
 * tool. A base interface — a generic CRUD contract, for instance — states each function's title, description,
 * and behavior hints once, and every service that {@link McpTool} exposes serves them.
 * A method-level {@code @McpTool} takes precedence over this. What neither states falls to the function
 * itself: its Javadoc for the description, and its name for the title half and the hints.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpToolInfo {

    /**
     * The LLM-facing description of what the tool does.
     */
    String description() default "";

    /**
     * The function half of the tool's display title, which the service half always leads.
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
