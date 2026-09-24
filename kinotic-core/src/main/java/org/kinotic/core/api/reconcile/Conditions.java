package org.kinotic.core.api.reconcile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Operations on a record's {@link Condition}s, which hold at most one condition of each type.
 */
public final class Conditions {

    private Conditions() {
    }

    /**
     * @param conditions a record's conditions
     * @param type       the type to look for
     * @return the record's condition of the given type, empty when it carries none
     */
    public static Optional<Condition> find(List<Condition> conditions, ConditionType type) {
        return conditions.stream().filter(condition -> condition.type() == type).findFirst();
    }

    /**
     * @param conditions a record's conditions
     * @param type       the type to look for
     * @return true when the record carries a condition of the given type
     */
    public static boolean has(List<Condition> conditions, ConditionType type) {
        return find(conditions, type).isPresent();
    }

    /**
     * The record's conditions with the given one set. A condition of the same type already set is kept
     * as it is, since what matters is when the inference was first made.
     *
     * @param conditions a record's conditions
     * @param condition  the condition to set
     * @return the conditions to store on the record
     */
    public static List<Condition> with(List<Condition> conditions, Condition condition) {
        List<Condition> ret;
        if (has(conditions, condition.type())) {
            ret = conditions;
        } else {
            ret = new ArrayList<>(conditions);
            ret.add(condition);
        }
        return ret;
    }

    /**
     * The record's conditions with any of the given type cleared.
     *
     * @param conditions a record's conditions
     * @param type       the type to clear
     * @return the conditions to store on the record
     */
    public static List<Condition> without(List<Condition> conditions, ConditionType type) {
        return conditions.stream().filter(condition -> condition.type() != type).toList();
    }
}
