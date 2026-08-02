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
 * A method-level {@code @McpTool} takes precedence over this, and this over a type-level {@code @McpTool},
 * for the title and description. The hints come from whichever of a method-level {@code @McpTool} or this
 * is nearest the function, and from the function name when it carries neither.
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
     * The human-readable display title for the tool.
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
