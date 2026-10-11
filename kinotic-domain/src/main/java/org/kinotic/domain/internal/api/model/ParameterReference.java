package org.kinotic.domain.internal.api.model;

import java.util.List;

/**
 * A reference of a check's id template to a parameter, resolved to where the parameter is read from in a
 * request: the parameter by its position in a client's positional body or its name among a tool call's
 * arguments, and the property path into it for a reference such as {@code entry.applicationId}.
 *
 * @param text      the reference as the template writes it, such as {@code entry.applicationId}
 * @param parameter the parameter's name, such as {@code entry}
 * @param position  the parameter's position among the function's parameters
 * @param path      the property path into the parameter, empty for the parameter's own value
 */
public record ParameterReference(String text, String parameter, int position, List<String> path) {
}
