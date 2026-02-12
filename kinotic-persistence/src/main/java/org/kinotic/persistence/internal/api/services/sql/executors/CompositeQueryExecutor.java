package org.kinotic.persistence.internal.api.services.sql.executors;

import org.kinotic.continuum.api.crud.Page;
import org.kinotic.continuum.api.crud.Pageable;
import org.kinotic.persistence.internal.api.services.sql.QueryContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/29/24.
 */
public class CompositeQueryExecutor implements QueryExecutor {

    private List<QueryExecutor> queryExecutors = new ArrayList<>();

    public CompositeQueryExecutor addExecutor(QueryExecutor executor) {
        queryExecutors.add(executor);
        return this;
    }

    @Override
    public <T> CompletableFuture<List<T>> execute(QueryContext context, Class<T> type) {
//         CompletableFuture.allOf(queryExecutors.stream()
//                                             .map(executor -> executor.execute(parameters, type, context))
//                                             .toArray(CompletableFuture[]::new));
        return null;
    }

    @Override
    public <T> CompletableFuture<Page<T>> executePage(QueryContext context, Pageable pageable,
                                                      Class<T> type) {
        return null;
    }
}
