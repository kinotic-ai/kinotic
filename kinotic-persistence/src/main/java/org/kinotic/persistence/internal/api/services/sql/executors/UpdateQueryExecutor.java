package org.kinotic.persistence.internal.api.services.sql.executors;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.persistence.internal.api.services.sql.QueryContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/28/24.
 */
@RequiredArgsConstructor
public class UpdateQueryExecutor implements QueryExecutor {

    // private final String statement;

    @Override
    public <T> CompletableFuture<List<T>> execute(QueryContext context, Class<T> type) {
        return null;
    }

    @Override
    public <T> CompletableFuture<Page<T>> executePage(QueryContext context, Pageable pageable,
                                                      Class<T> type) {
        return null;
    }
}
