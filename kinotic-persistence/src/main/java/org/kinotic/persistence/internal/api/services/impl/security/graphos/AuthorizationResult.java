package org.kinotic.persistence.internal.api.services.impl.security.graphos;

import java.util.Map;

/**
 * Represents the result of an authorization request
 * @param operationAllowed true if the operation is allowed
 * @param entityAllowed true if access to the entity is allowed
 * @param fieldResults a map of field names to if they are allowed
 */
public record AuthorizationResult(boolean operationAllowed, boolean entityAllowed, Map<String, Boolean> fieldResults) {
}
