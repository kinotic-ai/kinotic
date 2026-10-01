package org.kinotic.persistence.internal.api.services.sql.executors;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.OffsetPageable;
import org.kinotic.core.api.crud.Order;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.persistence.EntityDescriptor;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.internal.api.hooks.ReadPostProcessor;
import org.kinotic.persistence.internal.api.hooks.ReadPreProcessor;
import org.kinotic.persistence.internal.api.services.sql.QueryContext;
import org.kinotic.sql.domain.OrderBy;
import org.kinotic.sql.domain.SortDirection;
import org.kinotic.sql.domain.statements.SelectStatement;
import org.kinotic.sql.executor.QueryBuilder;

import java.util.List;

/**
 * Runs a SELECT as a search of the entity's index, confined to the tenants the context reads the way every read
 * of the entity is.
 * Created by Navíd Mitchell 🤪 on 4/28/24.
 */
public class SelectQueryExecutor extends AbstractQueryExecutor {

    // The most documents the list form returns for a statement with no LIMIT: one Elasticsearch SQL page, which
    // is what the list form of an aggregate returns
    private static final int DEFAULT_LIMIT = 1000;

    private final String queryName;
    private final SelectStatement select;
    private final Sort orderBy;
    private final CrudServiceTemplate crudServiceTemplate;
    private final ReadPreProcessor readPreProcessor;
    private final ReadPostProcessor readPostProcessor;

    public SelectQueryExecutor(EntityDescriptor entityDescriptor,
                               String queryName,
                               SelectStatement select,
                               CrudServiceTemplate crudServiceTemplate,
                               ReadPreProcessor readPreProcessor,
                               ReadPostProcessor readPostProcessor) {
        super(entityDescriptor);
        this.queryName = queryName;
        this.select = select;
        this.orderBy = Sort.by(select.orderBy().stream().map(SelectQueryExecutor::toOrder).toList());
        this.crudServiceTemplate = crudServiceTemplate;
        this.readPreProcessor = readPreProcessor;
        this.readPostProcessor = readPostProcessor;
    }

    @Override
    public <T> Future<List<T>> execute(QueryContext context, Class<T> type) {
        int limit = select.limit() != null ? select.limit() : DEFAULT_LIMIT;
        return executePage(context, Pageable.ofSize(limit), type).map(Page::getContent);
    }

    @Override
    public <T> Future<Page<T>> executePage(QueryContext context, Pageable pageable, Class<T> type) {
        Query condition = select.whereClause() != null
                ? QueryBuilder.buildQuery(select.whereClause(), context.getNamedParameters())
                : null;
        return crudServiceTemplate.search(entityDescriptor.itemIndex(),
                                          ordered(pageable),
                                          type,
                                          builder -> readPreProcessor.beforeSelect(entityDescriptor,
                                                                                   builder,
                                                                                   context.getEntityContext(),
                                                                                   condition,
                                                                                   select.columns()))
                                  .map(readPostProcessor.paranoidCheck(entityDescriptor, context.getEntityContext(), "NamedQuery " + queryName));
    }

    /**
     * The page settings with the statement's ORDER BY leading the sort, followed by any sort the caller gave.
     */
    private Pageable ordered(Pageable pageable) {
        Pageable ret;
        if (orderBy.isUnsorted()) {
            ret = pageable;
        } else {
            Sort sort = pageable.getSort() == null ? orderBy : orderBy.and(pageable.getSort());
            ret = switch (pageable) {
                case OffsetPageable offset -> Pageable.create(offset.getPageNumber(), offset.getPageSize(), sort);
                case CursorPageable cursor -> Pageable.create(cursor.getCursor(), cursor.getPageSize(), sort);
                default -> throw new IllegalArgumentException("Unsupported Pageable type: " + pageable.getClass().getName());
            };
        }
        return ret;
    }

    private static Order toOrder(OrderBy term) {
        return term.direction() == SortDirection.DESC ? Order.desc(term.field()) : Order.asc(term.field());
    }
}
