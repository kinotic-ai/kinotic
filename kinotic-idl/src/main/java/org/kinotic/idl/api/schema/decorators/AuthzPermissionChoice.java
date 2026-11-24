package org.kinotic.idl.api.schema.decorators;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * One permission a permission resolver may require of a request, with the reason a request needs it, such as
 * {@code can_edit} because the query is an UPDATE.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public final class AuthzPermissionChoice {

    /**
     * The short permission name, such as {@code can_edit}, of the type the check's permission is named for.
     */
    private String permission;

    /**
     * Why a request needs the permission, as a refusal states it, such as "the query is an UPDATE".
     */
    private String reason;

}
