package org.kinotic.idl.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method on a published ({@code @Publish}) service interface as a Model Context Protocol tool.
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
     * The human-readable display title for the tool. The tool name itself is always derived from the service's
     * qualified name and the method name, so it is unique system wide.
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
