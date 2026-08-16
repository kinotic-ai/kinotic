package org.kinotic.persistence.internal.api.services.sql.executors;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.persistence.api.model.NamedQueryOperation;
import org.kinotic.persistence.api.services.security.AuthorizationService;
import org.kinotic.persistence.internal.api.services.sql.QueryContext;

import java.util.List;

/**
 * Created By navidmitchell on 12/30/24
 */
@RequiredArgsConstructor
public class PreAuthorizationExecutor implements QueryExecutor{

    private final AuthorizationService<NamedQueryOperation> authorizationService;
    private final QueryExecutor delegate;

    @Override
    public <T> Future<List<T>> execute(QueryContext context, Class<T> type) {
        return authorizationService.authorize(NamedQueryOperation.EXECUTE, context.getEntityContext())
                                   .compose(v -> delegate.execute(
                                           context, type
                                   ));
    }

    @Override
    public <T> Future<Page<T>> executePage(QueryContext context,
                                           Pageable pageable,
                                           Class<T> type) {
        return authorizationService.authorize(NamedQueryOperation.EXECUTE_PAGE, context.getEntityContext())
                                   .compose(v -> delegate.executePage(
                                           context, pageable,
                                           type
                                   ));
    }
}
