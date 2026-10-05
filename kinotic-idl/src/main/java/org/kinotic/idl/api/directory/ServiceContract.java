package org.kinotic.idl.api.directory;

import java.util.List;

/**
 * The contract of a service a runtime outside the platform declares, as a TypeScript service's runtime
 * publishes it: where the service is addressed, the resource it declares and its functions. The platform
 * converts it to the {@link org.kinotic.idl.api.schema.ServiceDefinition} the directory stores, deriving each
 * function's check as it does for its own services, so a request to the service is checked the same way.
 *
 * @param namespace the service's namespace, such as {@code com.acme.billing}
 * @param name      the service's name
 * @param version   the version the service registers under
 * @param zone      the zone the service is addressed in, such as {@code app.acme.crm}
 * @param resource  the resource the service declares
 * @param functions the service's functions
 */
public record ServiceContract(String namespace,
                              String name,
                              String version,
                              String zone,
                              AuthzResourceDeclaration resource,
                              List<FunctionContract> functions) {

    public ServiceContract {
        functions = functions == null ? List.of() : List.copyOf(functions);
    }

    /**
     * The service's namespace and name joined with {@code '.'}, as a definition's qualified name is.
     */
    public String qualifiedName() {
        return namespace + "." + name;
    }
}
