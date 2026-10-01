package org.kinotic.sql.domain;

import java.util.List;

/**
 * A statement that acts on the documents of one or more tables, which can be addressed at other tables
 * without changing anything else about it: the data statements, as opposed to the ones that create
 * Elasticsearch objects.
 */
public interface TableStatement extends Statement {

    /**
     * @return the tables this statement addresses, in a fixed order per statement type
     */
    List<String> tableNames();

    /**
     * @param tableNames the tables to address instead, in the order of {@link #tableNames()}
     * @return a copy of this statement addressing those tables
     */
    TableStatement withTableNames(List<String> tableNames);

    /**
     * The one table of a statement that addresses a single table.
     *
     * @param tableNames the tables handed to {@link #withTableNames(List)}
     * @return the only table
     * @throws IllegalArgumentException if more or fewer than one table was handed over
     */
    static String single(List<String> tableNames) {
        if (tableNames.size() != 1) {
            throw new IllegalArgumentException("The statement addresses one table, not " + tableNames);
        }
        return tableNames.getFirst();
    }
}
