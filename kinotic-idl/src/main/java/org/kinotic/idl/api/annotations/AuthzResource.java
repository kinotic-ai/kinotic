package org.kinotic.idl.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the resource type a published ({@code @Publish}) service acts on, which makes every function of the
 * service subject to an authorization check at the gateway and contributes the type to the generated
 * authorization model.
 *
 * Each function's check is derived from its name and parameters, and {@link AuthzCheck} states what derivation
 * cannot: a function whose name derives no permission and carries no {@link AuthzCheck} fails service
 * registration, so a resource service never serves an unchecked function by accident.
 *
 * A resource's permissions are named {@code <type>_<permission>} in the model, so a role bundling
 * {@code project_can_edit} grants editing of projects wherever it is bound: on one project, or on an
 * ancestor, from which the permission inherits down to every project inside it.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthzResource {

    /**
     * The resource type, a single lowercase identifier such as {@code project}. The type of an entity row is
     * the entity definition's name, so a service type must not collide with one.
     */
    String value();

    /**
     * The type that contains this one, such as {@code application} for {@code project}. A grant made on the
     * parent inherits to every resource of this type inside it, and a function with no resource id of its own,
     * such as a listing or a create, is checked on the parent. Empty for a root type.
     */
    String parent() default "";

}
