package org.kinotic.persistence.internal.api.services.sql.executors;

import org.kinotic.continuum.core.api.crud.Page;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.kinotic.persistence.internal.api.services.sql.QueryContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Query executor for executing SQL queries against a data store
 * Created by Navíd Mitchell 🤪 on 4/28/24.
 */
public interface QueryExecutor {

    <T> CompletableFuture<List<T>> execute(QueryContext context,
                                           Class<T> type);

    <T> CompletableFuture<Page<T>> executePage(QueryContext context,
                                               Pageable pageable,
                                               Class<T> type);

}
