package org.kinotic.domain.internal.api.model;

import org.kinotic.domain.api.services.security.RequestPermissionResolver;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;

import java.util.List;

/**
 * What the authorizer keeps of a function's contract: its check, each parameter reference of the check's id
 * template resolved to where it is read from in a request, and, for a check whose permission depends on the
 * request, the resolver deciding it with the parameters the resolver reads.
 *
 * @param check             the function's check, or null for a function marked unchecked
 * @param references        the parameter references the check's id template makes
 * @param resolver          the resolver deciding the permission; null for a check naming its permission, and for
 *                          one naming a resolver this node does not have
 * @param resolverArguments the parameters the resolver reads, in the order it reads them
 */
public record FunctionSpec(AuthzCheckC3Decorator check,
                           List<ParameterReference> references,
                           RequestPermissionResolver resolver,
                           List<ParameterReference> resolverArguments) {

    /** The spec of a function its contract marks unchecked. */
    public static final FunctionSpec UNCHECKED = new FunctionSpec(null, List.of(), null, List.of());

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
