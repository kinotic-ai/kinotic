package org.kinotic.persistence.internal.api.services.sql;

import org.kinotic.persistence.api.model.NamedQueriesDefinition;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.internal.api.services.sql.executors.QueryExecutor;

/**
 * Created by Navíd Mitchell 🤪 on 4/29/24.
 */
public interface QueryExecutorFactory {

    /**
     * Creates a QueryExecutor for the given {@link EntityDefinition} and query name
     * @param entityDefinition the {@link EntityDefinition} to create the {@link QueryExecutor} for
     * @param queryName the name of the query to create the {@link QueryExecutor} for
     * @param namedQueriesDefinition the {@link NamedQueriesDefinition} that contains the query
     * @return the created {@link QueryExecutor}
     */
    QueryExecutor createQueryExecutor(EntityDefinition entityDefinition,
                                      String queryName,
                                      NamedQueriesDefinition namedQueriesDefinition);

}
