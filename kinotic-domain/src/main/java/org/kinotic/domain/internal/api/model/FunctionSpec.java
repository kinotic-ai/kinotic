package org.kinotic.domain.internal.api.model;

import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;

import java.util.List;

/**
 * What the authorizer keeps of a function's contract: its check, and each parameter reference of the check's id
 * template resolved to where it is read from in a request.
 *
 * @param check      the function's check, or null for a function marked unchecked
 * @param references the parameter references the check's id template makes
 */
public record FunctionSpec(AuthzCheckC3Decorator check, List<ParameterReference> references) {

    /** The spec of a function its contract marks unchecked. */
    public static final FunctionSpec UNCHECKED = new FunctionSpec(null, List.of());

    /**
     * @param text a reference of the check's id template, as the template writes it
     * @return the parameter reference it is, or null for a reference to the caller's scope
     */
    public ParameterReference reference(String text) {
        ParameterReference ret = null;
        for (ParameterReference reference : references) {
            if (reference.text().equals(text)) {
                ret = reference;
                break;
            }
        }
        return ret;
    }
}
