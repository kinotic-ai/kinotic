package org.kinotic.persistence.internal.api.services.sql.executors;

import lombok.RequiredArgsConstructor;
import org.kinotic.continuum.core.api.crud.Page;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.kinotic.persistence.api.domain.NamedQueryOperation;
import org.kinotic.persistence.api.services.security.AuthorizationService;
import org.kinotic.persistence.internal.api.services.sql.QueryContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created By navidmitchell on 12/30/24
 */
@RequiredArgsConstructor
public class PreAuthorizationExecutor implements QueryExecutor{

    private final AuthorizationService<NamedQueryOperation> authorizationService;
    private final QueryExecutor delegate;

    @Override
    public <T> CompletableFuture<List<T>> execute(QueryContext context, Class<T> type) {
        return authorizationService.authorize(NamedQueryOperation.EXECUTE, context.getEntityContext())
                                   .thenCompose(v -> delegate.execute(
                                           context, type
                                   ));
    }

    @Override
    public <T> CompletableFuture<Page<T>> executePage(QueryContext context,
                                                      Pageable pageable,
                                                      Class<T> type) {
        return authorizationService.authorize(NamedQueryOperation.EXECUTE_PAGE, context.getEntityContext())
                                   .thenCompose(v -> delegate.executePage(
                                           context, pageable,
                                           type
                                   ));
    }
}
