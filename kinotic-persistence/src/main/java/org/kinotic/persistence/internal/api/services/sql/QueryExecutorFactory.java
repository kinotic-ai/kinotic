package org.kinotic.persistence.internal.api.services.sql;

import org.kinotic.persistence.api.model.NamedQueriesDefinition;
import org.kinotic.persistence.api.model.EntityDescriptor;
import org.kinotic.persistence.internal.api.services.sql.executors.QueryExecutor;

/**
 * Created by Navíd Mitchell 🤪 on 4/29/24.
 */
public interface QueryExecutorFactory {

    /**
     * Creates a QueryExecutor for the given {@link EntityDescriptor} and query name
     * @param entityDescriptor the {@link EntityDescriptor} to create the {@link QueryExecutor} for
     * @param queryName the name of the query to create the {@link QueryExecutor} for
     * @param namedQueriesDefinition the {@link NamedQueriesDefinition} that contains the query
     * @return the created {@link QueryExecutor}
     */
    QueryExecutor createQueryExecutor(EntityDescriptor entityDescriptor,
                                      String queryName,
                                      NamedQueriesDefinition namedQueriesDefinition);

}
