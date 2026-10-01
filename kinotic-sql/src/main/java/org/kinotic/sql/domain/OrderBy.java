package org.kinotic.sql.domain;

/**
 * One term of a SELECT statement's ORDER BY clause.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 *
 * @param field     the field the rows are ordered by
 * @param direction the direction, {@link SortDirection#ASC} when the term names none
 */
public record OrderBy(String field, SortDirection direction) {
}
