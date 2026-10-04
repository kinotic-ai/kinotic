package org.kinotic.idl.api.schema.decorators;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * A role a resource service declares beside the built-in ones, as the {@link AuthzResourceC3Decorator}
 * carries it: the role's id and the short names of the permissions of the type it bundles.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class AuthzRoleDeclaration {

    /**
     * The role's id, {@code <type>.<level>}, such as {@code vm_node.registrar}.
     */
    private String id;

    /**
     * Short names of the type's permissions the role bundles.
     */
    private List<String> permissions = List.of();

}
