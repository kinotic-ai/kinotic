package org.kinotic.idl.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * States a part of one function's check that derivation gets wrong or cannot know: the permission, the type of
 * the resource, or its id. Every attribute left empty keeps the derived value, or the one the service declares on
 * {@link AuthzResource}, so a function states only the part derivation misses. Derivation is described on
 * {@link AuthzResource}; a function served with no check is marked {@link AuthzUnchecked} instead.
 *
 * <p>{@link #resourceId()} is a template over the function's parameters: {@code {nodeId}} is a parameter's value,
 * {@code {registration.id}} a property of an object parameter, and {@code {@organizationId}},
 * {@code {@applicationId}} or {@code {@tenantId}} the matching id of the caller's scope. {@link #resource()}
 * names a type and takes no template: what a request names is always an id.
 *
 * <p>Examples: {@code @AuthzCheck(permission = "can_report", resourceId = "{registration.id}")} on
 * {@code heartbeat(Registration registration)}, whose name derives no permission and whose id is inside the
 * body; {@code @AuthzCheck(resource = "platform", permission = "can_register_node")} on {@code registerNode} of
 * the service typed {@code vm_node}, checked on the platform, where the node is not yet, for a permission of the
 * node type.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthzCheck {

    /**
     * The permission the function needs, as a short name such as {@code can_view} or {@code can_report}. In the
     * model it is prefixed by the type it is about, so {@code can_report} on a {@code node} service is
     * {@code node_can_report}.
     */
    String permission() default "";

    /**
     * The type of the resource the check is made on, when it is not the service's own type, such as the parent
     * for a create. A type name, never a template.
     */
    String resource() default "";

    /**
     * The id of the resource the check is made on, as a template such as {@code {nodeId}} or
     * {@code {registration.id}}.
     */
    String resourceId() default "";

    /**
     * Short names of permissions on the same type that this permission also grants, so a role bundling
     * {@code can_deploy} declared with {@code implies = "can_view"} views what it deploys. The derived
     * permissions already imply each other: {@code can_delete} implies {@code can_edit}, which implies
     * {@code can_view}.
     */
    String[] implies() default {};

    /**
     * True for a function whose check must not be answered from the engine's caches, because a stale allow
     * would be a security event: removing or disabling a member, rotating a machine's secret.
     */
    boolean consistent() default false;

}
