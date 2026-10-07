package org.kinotic.domain.internal.api.model;

import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;

import java.util.List;

/**
 * What the authorizer keeps of a function's contract: its check, and the names of the parameters a request
 * carries, in the order a positional body lists them.
 *
 * @param check      the function's check, or null for an unchecked function
 * @param parameters the parameter names in body order
 */
public record FunctionSpec(AuthzCheckC3Decorator check, List<String> parameters) {

    /** The spec of an unchecked function: one declared with no check, or one no contract covers. */
    public static final FunctionSpec UNCHECKED = new FunctionSpec(null, List.of());
}
