package org.kinotic.persistence.internal.api.services.sql;

import org.kinotic.structures.api.domain.NamedQueriesDefinition;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.persistence.internal.api.services.sql.executors.QueryExecutor;

/**
 * Created by Navíd Mitchell 🤪 on 4/29/24.
 */
public interface QueryExecutorFactory {

    /**
     * Creates a QueryExecutor for the given {@link Structure} and query name
     * @param structure the {@link Structure} to create the {@link QueryExecutor} for
     * @param queryName the name of the query to create the {@link QueryExecutor} for
     * @param namedQueriesDefinition the {@link NamedQueriesDefinition} that contains the query
     * @return the created {@link QueryExecutor}
     */
    QueryExecutor createQueryExecutor(Structure structure,
                                      String queryName,
                                      NamedQueriesDefinition namedQueriesDefinition);

}
