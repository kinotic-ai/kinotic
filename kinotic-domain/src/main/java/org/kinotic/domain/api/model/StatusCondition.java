package org.kinotic.domain.api.model;

import java.util.Date;

/**
 * Something a watcher has inferred about a record beside what the record's authority reports: a node
 * gone silent, a runner gone from the cluster. Each {@link StatusConditionType} names the one writer
 * that sets it and the one that clears it, so a condition never overwrites the authority's own fields
 * and the authority's word never overwrites a condition it did not set. A record holds at most one
 * condition of each type.
 *
 * @param type    what was inferred
 * @param message why, for an operator
 * @param since   when the inference was first made
 */
public record StatusCondition(StatusConditionType type, String message, Date since) {
}
