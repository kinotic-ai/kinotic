package org.kinotic.idl.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a published service as acting on one type of resource, so that every function of it is authorized at
 * the gateway before the service sees the request. The type and the permissions its functions need become part
 * of the generated authorization model, where roles bundle permissions and grants bind roles to callers.
 *
 * <p>A function's check has three parts: the permission it needs, the type of the resource it is checked on,
 * and the id of that resource. Each part is derived from the function's name and parameters where it can be;
 * {@link AuthzCheck} states a part derivation gets wrong or cannot know, and {@link AuthzUnchecked} marks a
 * function served with no check. A function left with no check and no marker fails the service's
 * registration, so no function of a resource service is served unchecked by accident.
 *
 * <p>The permission is derived from the first word of the function's name: {@code find}, {@code get},
 * {@code count}, {@code search} and {@code list} need {@code can_view}; {@code save}, {@code update} and
 * {@code set} need {@code can_edit}; {@code delete} and {@code remove} need {@code can_delete}; {@code create}
 * needs {@code can_edit} of this type on the parent. {@code can_delete} implies {@code can_edit}, which implies
 * {@code can_view}.
 *
 * <p>The resource is derived from the parameters: its id is the {@code String} parameter named {@code id} or
 * {@code <type>Id} ({@code projectId} for the type {@code project}), else the id of the first
 * {@code Identifiable} parameter. A function with neither is checked on the parent, whose id is found the same
 * way with the parent's name, else on the caller's own scope.
 *
 * <p>Example: with {@code @AuthzResource(value = "project", parent = "application")}, {@code findById(String id)}
 * is checked for {@code project_can_view} on the project {@code id} names, {@code save(Project project)} for
 * {@code project_can_edit} on the project {@code project.getId()} names, and
 * {@code findAll(String applicationId, Pageable pageable)} for {@code project_can_view} on the application
 * {@code applicationId} names, with nothing declared on them.
 *
 * <p>Permissions are named {@code <type>_<permission>} in the model, and a role bundling one grants it wherever
 * the role is bound: on one object of the type, or on an ancestor, from which it inherits to every object of
 * the type inside it.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthzResource {

    /**
     * The resource type, a single lowercase identifier such as {@code project}. It names the type in the model,
     * prefixes the type's permissions ({@code project_can_view}) and roles ({@code project.editor}), and is the
     * type of the resource every function is checked on unless the function names another with
     * {@link AuthzCheck}. Several services may declare one type; each adds its functions' permissions to it.
     *
     * <p>A type is a constant. Which resource of the type a function acts on is named by the id its check resolves
     * from the request, so a service acting on whichever of many things a request names declares the one type
     * those things share and takes the thing's id from the request, as the entity functions take the
     * definition's. A type must not collide with one the model holds for another reason.
     */
    String value();

    /**
     * The type that contains this one, such as {@code application} for {@code project}. A grant made on an
     * resource of the parent type inherits to every resource of this type inside it, and a function naming no
     * resource of its own, such as a listing or a create, is checked on the parent. Empty for a root type.
     */
    String parent() default "";

    /**
     * The resource every function of the service is checked on, for a service whose functions act on one
     * resource the request does not name: a template over the caller's scope, {@code {@organizationId}},
     * {@code {@applicationId}} or {@code {@tenantId}}, or a literal id. A function naming its own resource with
     * {@link AuthzCheck} is unaffected.
     */
    String resourceId() default "";

    /**
     * The permission every function of the service needs, as a short name such as {@code can_manage}, for a
     * service whose functions all need the one permission whatever their names. A function declaring a
     * permission of its own with {@link AuthzCheck}, on this interface or one it extends, keeps it. Name the
     * service's strictest permission: a function added later needs it until it declares a weaker one.
     */
    String permission() default "";

    /**
     * Roles of this type beside the built-in {@code viewer}, {@code editor} and {@code admin}, each bundling
     * permissions the type's functions need. The model defines a role once for the type, wherever the type is
     * declared.
     */
    AuthzRole[] roles() default {};

}
