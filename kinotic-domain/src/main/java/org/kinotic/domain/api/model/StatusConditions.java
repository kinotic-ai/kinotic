package org.kinotic.domain.api.model;

import java.util.List;
import java.util.Optional;

/**
 * Reads over a record's {@link StatusCondition}s, which hold at most one condition of each type.
 */
public final class StatusConditions {

    private StatusConditions() {
    }

    /**
     * @param conditions a record's conditions
     * @param type       the type to look for
     * @return the record's condition of the given type, empty when it carries none
     */
    public static Optional<StatusCondition> find(List<StatusCondition> conditions, StatusConditionType type) {
        return conditions.stream().filter(condition -> condition.type() == type).findFirst();
    }

    /**
     * @param conditions a record's conditions
     * @param type       the type to look for
     * @return true when the record carries a condition of the given type
     */
    public static boolean has(List<StatusCondition> conditions, StatusConditionType type) {
        return find(conditions, type).isPresent();
    }
}
