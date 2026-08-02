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
 * every function becomes a tool and this annotation states the service half of their {@link #title}, while
 * each function is described by its own {@code @McpTool} or {@link McpToolInfo}, and failing that by its
 * Javadoc and its name, so each tool stays individually recognizable to an LLM caller. Either may sit
 * anywhere in a function's hierarchy, which is how a base interface describes functions another service
 * exposes.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

    /**
     * The LLM-facing description of what the tool does, read on a method. When empty, the function's Javadoc
     * describes the tool, and failing that the function name split into a sentence
     * ({@code findByRepoFullName} becomes {@code "Find by repo full name"}).
     */
    String description() default "";

    /**
     * Half of the tool's human-readable display title, which is always a service half and a function half
     * joined by a space. On the service interface this states the service half, on a method the function
     * half; each half a declaration leaves empty is split out of the name it stands for, so
     * {@code ProjectService.findByRepoFullName} titles as {@code "Project Service Find By Repo Full Name"}.
     * Carrying the service half on every tool is what keeps a title recognizable when many services expose
     * a function of the same name. The tool name itself is always derived from the service's qualified name
     * and the method name, so it is unique system wide.
     */
    String title() default "";

    /**
     * Indicates the tool does not modify its environment. Declared on a method, this and the other two hints
     * are served exactly as written. A function swept in by a type-level {@code @McpTool}, carrying neither
     * a {@code @McpTool} nor a {@link McpToolInfo} of its own, is hinted by every word of its name: a word
     * that replaces or removes state ({@code save}, {@code delete}) makes it destructive and idempotent, a
     * {@code createIfNotExist} idempotent, a word that adds or acts ({@code create}, {@code send}) nothing,
     * and only a name whose verbs all read ({@code find}, {@code get}, {@code peopleCount}) read-only.
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
