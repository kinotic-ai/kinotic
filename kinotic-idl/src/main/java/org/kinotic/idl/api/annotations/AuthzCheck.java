package org.kinotic.idl.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * States the authorization check of one function of an {@link AuthzResource} service where derivation from the
 * function's name and parameters comes out wrong or comes out empty. Every value left empty keeps the derived
 * one, so a function usually states only the part derivation cannot know.
 *
 * Derivation: a name starting with {@code find}, {@code get}, {@code count}, {@code search} or {@code list}
 * requires {@code can_view}; {@code save}, {@code update} or {@code set} requires {@code can_edit};
 * {@code delete} or {@code remove} requires {@code can_delete}; {@code create} requires {@code can_edit} of
 * this type on the parent resource. The resource id is the {@code String} parameter named {@code id} or after
 * the type ({@code projectId} for {@code project}), else the {@code id} of the first {@code Identifiable}
 * parameter; a function with neither is checked on the parent resource, whose id is the parameter named after
 * it ({@code applicationId} for a parent {@code application}), else that property of an object parameter,
 * else the caller's own scope.
 *
 * Templates: {@link #resource()} and {@link #objectId()} are templates over the function's parameters.
 * {@code {projectId}} is the parameter's value, {@code {registration.id}} a property inside an object
 * parameter, and {@code {@organizationId}}, {@code {@applicationId}} or {@code {@tenantId}} the matching id
 * of the calling participant's scope.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthzCheck {

    /**
     * The permission the function requires, as a short name such as {@code can_view} or {@code can_report}.
     * Its model name is prefixed by the type it is about, so {@code can_report} on a {@code vm_node} service is
     * {@code vm_node_can_report}.
     */
    String permission() default "";

    /**
     * The type of the object the check is made on, as a literal or a template, when it is not this service's
     * own type: the parent for a create, or {@code {entityDefinitionId}} for a function whose type is an
     * argument.
     */
    String resource() default "";

    /**
     * The id of the object the check is made on, as a template such as {@code {projectId}} or
     * {@code {registration.id}}.
     */
    String objectId() default "";

    /**
     * Short names of permissions on the same type that holding this permission also grants, so a role that
     * bundles {@code can_deploy} with {@code implies = "can_view"} views what it deploys. The derived
     * permissions already imply each other: {@code can_delete} implies {@code can_edit}, which implies
     * {@code can_view}.
     */
    String[] implies() default {};

    /**
     * True for a function any caller the zone admits may call, such as a listing of the caller's own access:
     * the function is published with no check, as a function of a service without {@link AuthzResource} is.
     * Nothing else may be declared beside it.
     */
    boolean zoneOnly() default false;

    /**
     * True for a function whose check must not answer from the engine's caches, because a stale allow would
     * be a security event: removing or disabling a member, rotating a machine's secret.
     */
    boolean consistent() default false;

}
