package org.kinotic.idl.api.directory;

import java.util.Set;

/**
 * Parameter types a service contract leaves out. A parameter of one of these types is supplied to the function
 * by the platform when it is invoked, never by the request, so no function advertises it: a request body
 * carries the remaining parameters in declaration order, and a reader of the contract indexes them the same
 * way. Each module contributes one bean naming the types it supplies, and the {@link SchemaFactory} honors
 * them all.
 *
 * @param types the parameter types to leave out, matched by assignability
 */
public record SkippedParameterTypes(Set<Class<?>> types) {

    /**
     * Whether a parameter of the given type is left out of the contract.
     */
    public boolean skips(Class<?> parameterType) {
        boolean ret = false;
        for (Class<?> type : types) {
            if (type.isAssignableFrom(parameterType)) {
                ret = true;
                break;
            }
        }
        return ret;
    }

}
