package org.kinotic.core.api.reconcile;

import java.util.Date;

/**
 * Something the platform has inferred about a record that the record's authority has not confirmed —
 * a node gone silent, a runner gone from the cluster. Whoever infers it sets it, and the authority's
 * next word clears it, so a record with no conditions is one whose every observation came from its
 * authority. A record holds at most one condition of each type.
 *
 * @param type    what was inferred
 * @param message why, for an operator
 * @param since   when the inference was first made
 */
public record Condition(ConditionType type, String message, Date since) {
}
