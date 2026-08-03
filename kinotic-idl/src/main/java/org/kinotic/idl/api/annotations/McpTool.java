package org.kinotic.idl.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exposes a published ({@code @Publish}) service's functions as Model Context Protocol tools. Put it on a
 * method to expose that one function, or on the service interface to expose every function. On a method it
 * works from the interface or from the implementation's override.
 *
 * A tool's title is the service's title followed by the function's: the annotation on the interface sets the
 * first, the one on the method sets the second. The description and hints are read from the method only.
 * Anything the method leaves empty comes from the function itself: its name becomes its half of the title,
 * its Javadoc becomes the description, and the words in its name become the hints.
 *
 * Use {@link McpToolInfo} to describe a function without exposing it. Both annotations are inherited, so a
 * base interface such as a CRUD contract can describe functions that only its subtypes expose.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

    /**
     * What the tool does, in the words an LLM reads to decide whether to call it. Only read on a method.
     * When it is empty the function's Javadoc is used, and when the function has none, its name as a
     * sentence: {@code findByRepoFullName} becomes {@code "Find by repo full name"}.
     */
    String description() default "";

    /**
     * Half of the tool's display title. On the service interface it is the service's half, on a method it is
     * the function's, and the two are joined by a space. An empty half is built from the name it stands for,
     * so {@code ProjectService.findByRepoFullName} is titled
     * {@code "Project Service Find By Repo Full Name"}. Every tool carries the service's half, which is what
     * keeps a title recognizable when several services have a function of the same name. This never affects
     * the tool's name, which is always built from the service's qualified name and the method name.
     */
    String title() default "";

    /**
     * Indicates the tool does not modify its environment. All four hints are read together from the
     * method's annotation, exactly as it writes them. A function with no annotation of its own is hinted by
     * the words in its name instead: {@code save} or {@code delete} make it destructive and idempotent, a
     * name ending in {@code IfNotExist} makes it idempotent, {@code create} or {@code send} leave all three
     * false, and a name that only reads ({@code find}, {@code get}, {@code peopleCount}) makes it read-only.
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

    /**
     * Indicates the tool reaches a system outside the platform, such as a third-party API. A function that
     * works against the platform's own data leaves this false, which is what an MCP host reads to decide how
     * much scrutiny a call deserves. MCP itself defaults this hint to true, so a tool that never states it
     * would otherwise be served as if it called out to the world.
     */
    boolean openWorldHint() default false;

}
