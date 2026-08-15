package org.kinotic.persistence.internal.api.services.sql.executors;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.persistence.internal.api.services.sql.QueryContext;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 4/28/24.
 */
public class SelectQueryExecutor implements QueryExecutor {

    @Override
    public <T> Future<List<T>> execute(QueryContext context,
                                       Class<T> type) {
        return null;
    }

    @Override
    public <T> Future<Page<T>> executePage(QueryContext context,
                                           Pageable pageable,
                                           Class<T> type) {
        return null;
    }
}
