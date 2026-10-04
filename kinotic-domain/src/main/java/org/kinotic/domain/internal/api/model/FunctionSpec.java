package org.kinotic.domain.internal.api.model;

import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;

import java.util.List;

/**
 * What the authorizer keeps of a function's contract: its check, and the names of the parameters a request
 * carries, in the order a positional body lists them.
 *
 * @param check      the function's check, or null for a function its zone alone admits
 * @param parameters the parameter names in body order
 */
public record FunctionSpec(AuthzCheckC3Decorator check, List<String> parameters) {

    /** The spec of a function with no contract or no check. */
    public static final FunctionSpec ZONE_ONLY = new FunctionSpec(null, List.of());
}
