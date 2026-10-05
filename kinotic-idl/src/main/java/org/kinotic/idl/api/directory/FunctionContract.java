package org.kinotic.idl.api.directory;

import java.util.List;

/**
 * One function of a {@link ServiceContract}: its name, the names of the parameters a request carries, in the
 * order a request carries them, and what the function declares about its check, or null when it declares
 * nothing.
 *
 * @param name       the function's name, which a request addresses it by
 * @param parameters the parameter names in order
 * @param check      the declared check, or null
 */
public record FunctionContract(String name, List<String> parameters, AuthzCheckDeclaration check) {

    public FunctionContract {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}
