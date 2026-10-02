package org.kinotic.auth.api.engine;

import java.util.List;

/**
 * A single authorization decision request: the authenticated participant, the action being
 * attempted, and the arguments the action was invoked with.
 * <p>
 * Argument data is carried as the raw JSON exactly as it arrives at the gateway. {@code argumentsJson}
 * is a JSON array whose elements are mapped, by position, onto {@code parameterNames} to form the
 * named object an {@link AuthorizationEngine} evaluates policies against — so {@code parameterNames.get(0)}
 * names {@code argumentsJson[0]}, and a policy path like {@code order.amount} resolves against the
 * element named {@code order}.
 *
 * @param principalId             identifier of the authenticated participant (e.g. {@code "user-123"})
 * @param principalAttributesJson JSON object of participant attributes (e.g. {@code {"roles":["finance"]}})
 * @param action                  the action being authorized; must match a registered policy
 * @param argumentsJson           JSON array of the raw method arguments (e.g. {@code [{"amount":25000}]})
 * @param parameterNames          method parameter names, positionally aligned with {@code argumentsJson}
 */
public record AuthorizationRequest(String principalId,
                                   String principalAttributesJson,
                                   String action,
                                   String argumentsJson,
                                   List<String> parameterNames) {
}
