package org.kinotic.idl.api.directory;

import org.kinotic.idl.api.schema.decorators.AuthzPermissionChoice;

import java.util.List;

/**
 * What a function's contract records of the resolver deciding its permission per request, for a check declared
 * with {@code @AuthzCheck(permissionResolver = ...)}: the parameters it reads and every permission it may
 * require, so the model holds each of them and the gateway refuses any other.
 */
public interface AuthzPermissionResolver {

    /**
     * @return the name a check gives to select this resolver, one of the {@code *_PERMISSION_RESOLVER} constants
     *         of {@link org.kinotic.idl.api.utils.AuthzConstants}
     */
    String name();

    /**
     * @return the names of the parameters this resolver reads, in the order it receives their values; a function
     *         whose check names this resolver must carry each of them
     */
    List<String> parameters();

    /**
     * @return every permission this resolver may require, each with the reason a request needs it
     */
    List<AuthzPermissionChoice> possiblePermissions();
}
