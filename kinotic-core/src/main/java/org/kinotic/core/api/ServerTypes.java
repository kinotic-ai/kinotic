package org.kinotic.core.api;

import org.apache.commons.lang3.Validate;

import java.util.EnumSet;
import java.util.Set;

/**
 * The {@link ServerType}s a server is of, which its nodes advertise to the cluster so the nodes of a type can be
 * selected. The server module declares it as a bean; a server that declares none is of every type, as one server
 * running every module is.
 *
 * @param types the types the server is of
 */
public record ServerTypes(Set<ServerType> types) {

    public ServerTypes {
        Validate.notEmpty(types, "types must not be empty");
        types = Set.copyOf(types);
    }

    /** A server of the given types. */
    public static ServerTypes of(ServerType first, ServerType... more) {
        return new ServerTypes(EnumSet.of(first, more));
    }

    /** A server of every type. */
    public static ServerTypes every() {
        return new ServerTypes(EnumSet.allOf(ServerType.class));
    }

}
