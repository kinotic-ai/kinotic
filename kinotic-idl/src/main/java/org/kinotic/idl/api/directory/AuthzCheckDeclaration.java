package org.kinotic.idl.api.directory;

import java.util.List;

/**
 * What a function declares about its check, as {@code @AuthzCheck} and its TypeScript mirror declare it;
 * whatever is left out, null here, is derived from the function's name and parameters.
 *
 * @param permission the short permission name, such as {@code can_deploy}
 * @param resource   the type of the object the check is made on, when it is not the service's own
 * @param objectId   the id template of the object, over the function's parameters and the caller's scope
 * @param implies    short names of permissions this one implies
 * @param zoneOnly   true for a function any caller the zone admits may call, which carries no check
 * @param consistent true for a check that must answer from the stored relationships
 */
public record AuthzCheckDeclaration(String permission,
                                    String resource,
                                    String objectId,
                                    List<String> implies,
                                    Boolean zoneOnly,
                                    Boolean consistent) {

    public AuthzCheckDeclaration {
        implies = implies == null ? List.of() : List.copyOf(implies);
        // a client's contract leaves a flag out where it is not set, and the mapper refuses a missing primitive
        zoneOnly = zoneOnly != null && zoneOnly;
        consistent = consistent != null && consistent;
    }
}
