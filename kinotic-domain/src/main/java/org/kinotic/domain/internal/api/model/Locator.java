package org.kinotic.domain.internal.api.model;

import java.util.List;

/**
 * Where a parameter reference of a check's id template is read from in a request: the parameter by its position
 * in a positional body or its name in a named one, and the property path into it for a reference such as
 * {@code entry.applicationId}.
 *
 * @param reference the reference as the template writes it, such as {@code entry.applicationId}
 * @param parameter the parameter's name, such as {@code entry}
 * @param position  the parameter's position among the function's parameters
 * @param path      the property path into the parameter, empty for the parameter's own value
 */
public record Locator(String reference, String parameter, int position, List<String> path) {
}
