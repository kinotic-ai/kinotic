package org.kinotic.domain.internal.api.model;

import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;

import java.util.List;

/**
 * What the authorizer keeps of a function's contract: its check, and where each parameter reference of the
 * check's id template is read from in a request.
 *
 * @param check    the function's check, or null for an unchecked function
 * @param locators the locator of each parameter reference the check's id makes
 */
public record FunctionSpec(AuthzCheckC3Decorator check, List<Locator> locators) {

    /** The spec of a function its contract marks unchecked. */
    public static final FunctionSpec UNCHECKED = new FunctionSpec(null, List.of());

    /**
     * @param reference a parameter reference of the check's id template
     * @return where it is read from, or null for a reference to the caller's scope
     */
    public Locator locator(String reference) {
        Locator ret = null;
        for (Locator locator : locators) {
            if (locator.reference().equals(reference)) {
                ret = locator;
                break;
            }
        }
        return ret;
    }
}
