package org.kinotic.idl.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a published ({@code @Publish}) service's functions as Model Context Protocol tools. On a method —
 * declared on the service interface or on the implementation's override — that method becomes a callable
 * tool whose {@link #description} and hints are surfaced to LLM callers. On the service interface itself,
 * every function becomes a tool carrying the type-level description and hints, and a method-level
 * {@code @McpTool} overrides them for that method. An empty {@link #description} or {@link #title} is
 * derived from the function name, so each tool stays individually recognizable to an LLM caller.
 * A function exposed here serves the metadata a {@link McpToolInfo} on its declaration states, which is how
 * a base interface describes functions it does not itself expose.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

    /**
     * The LLM-facing description of what the tool does. When empty, the function name is split into a
     * sentence and used instead ({@code findByRepoFullName} becomes {@code "Find by repo full name"}).
     */
    String description() default "";

    /**
     * The human-readable display title for the tool. When empty, the service interface's simple name and
     * the function name are each split into a capitalized phrase and joined
     * ({@code ProjectService.findByRepoFullName} becomes {@code "Project Service Find By Repo Full Name"}),
     * so a title stays recognizable among the same function name on many services. The tool name itself is
     * always derived from the service's qualified name and the method name, so it is unique system wide.
     */
    String title() default "";

    /**
     * Indicates the tool does not modify its environment. Leaving all three hints unset takes them from the
     * first declaration stating any of them, and finally from every word of the function name: a word that
     * replaces or removes state ({@code save}, {@code delete}) makes it destructive and idempotent, a
     * {@code createIfNotExist} idempotent, a word that adds or acts ({@code create}, {@code send}) states
     * nothing, and only a name whose verbs all read ({@code find}, {@code get}, {@code peopleCount}) is
     * read-only.
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
