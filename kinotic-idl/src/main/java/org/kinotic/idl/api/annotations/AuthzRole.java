package org.kinotic.idl.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A role an {@link AuthzResource} declares beside the built-in ones: a bundle of the type's own permissions
 * that the generated model defines, named like a built-in role and granted the same way. A type declares a
 * role when the built-in levels do not fit how it is used: the one permission a machine needs to register a
 * node, or the two a registered node reports with.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthzRole {

    /**
     * The role's id, {@code <type>.<level>} as a built-in role's is, such as {@code vm_node.registrar}. The
     * type is the declaring resource's, and the level is an identifier no built-in role of the type has.
     */
    String id();

    /**
     * Short names of the permissions the role bundles, each one a permission some function of the type
     * requires, such as {@code can_register_node}.
     */
    String[] permissions();

}
