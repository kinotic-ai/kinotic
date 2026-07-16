package org.kinotic.core.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method on a {@link Publish}ed service interface as a Model Context Protocol tool.
 * A service is MCP-exposed when at least one of its methods carries this annotation, and each annotated method
 * becomes a callable tool whose {@link #description} and hints are surfaced to LLM callers.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

    /**
     * The LLM-facing description of what the tool does.
     */
    String description();

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
