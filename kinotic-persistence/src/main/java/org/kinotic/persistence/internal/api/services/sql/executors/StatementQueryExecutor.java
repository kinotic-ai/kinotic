package org.kinotic.persistence.internal.api.services.sql.executors;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.domain.api.model.persistence.EntityDescriptor;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.domain.api.services.EntityStatementResolver;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.internal.api.services.sql.QueryContext;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.executor.StatementExecutor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Runs a write, INSERT, UPDATE or DELETE, confined to the one tenant the context writes. The result is a single
 * row, {@code {"count": n}}, holding the number of documents written.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 */
public class StatementQueryExecutor extends AbstractQueryExecutor {

    private final Statement statement;
    private final EntityStatementResolver entityStatementResolver;
    private final StatementExecutor<Statement, ?> statementExecutor;
    private final JsonMapper jsonMapper;

    public StatementQueryExecutor(EntityDescriptor entityDescriptor,
                                  Statement statement,
                                  EntityStatementResolver entityStatementResolver,
                                  StatementExecutor<Statement, ?> statementExecutor,
                                  JsonMapper jsonMapper) {
        super(entityDescriptor);
        this.statement = statement;
        this.entityStatementResolver = entityStatementResolver;
        this.statementExecutor = statementExecutor;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public <T> Future<List<T>> execute(QueryContext context, Class<T> type) {
        Map<String, Object> parameters = context.getNamedParameters();
        String tenantId = entityDescriptor.multiTenancyType() == MultiTenancyType.SHARED ? writeTenant(context.getEntityContext()) : null;
        Statement confined = entityStatementResolver.confine(statement, entityDescriptor, tenantId, parameters);
        return KinoticUtil.toFuture(statementExecutor.executeQuery(confined, parameters))
                                  .map(result -> List.of(countRow(result, type)));
    }

    @Override
    public <T> Future<Page<T>> executePage(QueryContext context, Pageable pageable, Class<T> type) {
        return Future.failedFuture(new IllegalArgumentException("A named query that writes returns a count, not a page"));
    }

    /**
     * The one tenant a write acts on: the participant's own, or the one tenant an admin selects.
     */
    private static String writeTenant(EntityContext context) {
        String ret;
        if (context.hasTenantSelection()) {
            Validate.isTrue(context.getTenantSelection().size() == 1 && !context.selectsAllTenants(),
                            "A named query that writes acts on one tenant, select exactly one");
            ret = context.getTenantSelection().getFirst();
        } else {
            ret = context.requireTenantId();
        }
        return ret;
    }

    private <T> T countRow(Object result, Class<T> type) {
        // UPDATE and DELETE complete with the number of documents they touched, INSERT with no value for its one document
        long count = result instanceof Number number ? number.longValue() : 1;
        Map<String, Object> row = Map.of("count", count);
        T ret;
        if (RawJson.class.isAssignableFrom(type)) {
            ret = type.cast(new RawJson(jsonMapper.writeValueAsBytes(row)));
        } else {
            ret = jsonMapper.convertValue(row, type);
        }
        return ret;
    }
}
