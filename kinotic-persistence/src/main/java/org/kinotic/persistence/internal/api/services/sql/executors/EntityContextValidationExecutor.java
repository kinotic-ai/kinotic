package org.kinotic.persistence.internal.api.services.sql.executors;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityExceptionFactory;
import org.kinotic.domain.api.model.persistence.EntityDescriptor;
import org.kinotic.persistence.internal.api.services.sql.QueryContext;
import org.kinotic.persistence.internal.utils.PersistenceUtil;

import java.util.List;

/**
 * A {@link QueryExecutor} that validates the tenants the {@link QueryContext}'s entity context acts on before
 * delegating to another {@link QueryExecutor}. It follows the executor that applies the query's tenant selection
 * to the context, so the selection it validates is the one the query runs with.
 */
@RequiredArgsConstructor
public class EntityContextValidationExecutor implements QueryExecutor {

    private final EntityDescriptor entityDescriptor;
    private final QueryExecutor delegate;
    private final SecurityExceptionFactory securityExceptions;

    @Override
    public <T> Future<List<T>> execute(QueryContext context, Class<T> type) {
        return PersistenceUtil.validateEntityContext(entityDescriptor, context.getEntityContext(), securityExceptions)
                              .compose(v -> delegate.execute(context, type));
    }

    @Override
    public <T> Future<Page<T>> executePage(QueryContext context, Pageable pageable, Class<T> type) {
        return PersistenceUtil.validateEntityContext(entityDescriptor, context.getEntityContext(), securityExceptions)
                              .compose(v -> delegate.executePage(context, pageable, type));
    }

}
