package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.config.DomainPersistenceProperties;
import org.kinotic.domain.api.model.AppHost;
import org.kinotic.domain.api.model.persistence.EntityDescriptor;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.api.services.EntityStatementResolver;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.WhereClause;
import org.kinotic.sql.domain.statements.DeleteStatement;
import org.kinotic.sql.domain.statements.InsertStatement;
import org.kinotic.sql.domain.statements.ReindexStatement;
import org.kinotic.sql.domain.statements.SelectStatement;
import org.kinotic.sql.domain.statements.UpdateStatement;
import org.kinotic.sql.executor.ParameterUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 */
@Component
@RequiredArgsConstructor
public class DefaultEntityStatementResolver implements EntityStatementResolver {

    private final EntityDefinitionRepository entityDefinitionRepository;
    private final DomainPersistenceProperties domainPersistenceProperties;

    @Override
    public Future<List<Statement>> resolve(List<Statement> statements, AppHost application) {
        // one lookup per distinct name, shared by every statement that names the entity
        Map<String, Future<EntityDescriptor>> lookups = new LinkedHashMap<>();
        for (Statement statement : statements) {
            for (String name : names(statement)) {
                lookups.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> requireEntity(name, application));
            }
        }
        return Future.all(new ArrayList<>(lookups.values()))
                     .map(v -> {
                         Function<String, EntityDescriptor> entity = name -> lookups.get(name.toLowerCase(Locale.ROOT)).result();
                         return statements.stream()
                                          .map(statement -> statement instanceof InsertStatement insert
                                                  ? identifyFromRow(insert, entity.apply(insert.tableName()))
                                                  : address(statement, entity))
                                          .toList();
                     });
    }

    @Override
    public List<Statement> resolve(List<Statement> statements, EntityDescriptor entity) {
        Function<String, EntityDescriptor> own = name -> {
            Validate.isTrue(entity.name().equalsIgnoreCase(name), "A named query of %s acts on %s, not %s",
                            entity.name(), entity.name(), name);
            return entity;
        };
        return statements.stream().map(statement -> address(statement, own)).toList();
    }

    @Override
    public Statement confine(Statement statement, EntityDescriptor entity, String tenantId, Map<String, Object> parameters) {
        return switch (statement) {
            case InsertStatement insert -> {
                InsertStatement row = tenantId != null ? withTenant(insert, entity, tenantId, parameters) : insert;
                yield identify(row, entity, tenantId, rowValue(row, entity, entity.idFieldName(), parameters));
            }
            case UpdateStatement update -> {
                if (tenantId != null) {
                    Validate.isTrue(!update.assignments().containsKey(tenantField(entity)),
                                    "UPDATE %s cannot assign %s", entity.name(), tenantField(entity));
                }
                yield new UpdateStatement(update.tableName(), update.assignments(),
                                          confine(update.whereClause(), entity, tenantId), update.refresh());
            }
            case DeleteStatement delete -> new DeleteStatement(delete.tableName(), confine(delete.whereClause(), entity, tenantId),
                                                               delete.refresh());
            default -> throw new IllegalArgumentException(kind(statement) + " is not a write");
        };
    }

    private Future<EntityDescriptor> requireEntity(String name, AppHost application) {
        String organizationId = application.organizationId();
        String id = DomainUtil.createEntityDefinitionId(organizationId, application.applicationId(), name);
        return entityDefinitionRepository.findById(id, organizationId)
                                         .map(definition -> {
                                             Validate.isTrue(definition != null && definition.isPublished(),
                                                             "Application %s has no published entity named %s",
                                                             application.applicationId(), name);
                                             return definition.toDescriptor();
                                         });
    }

    /**
     * The entity names a statement carries.
     */
    private static List<String> names(Statement statement) {
        return switch (statement) {
            case InsertStatement insert -> List.of(insert.tableName());
            case UpdateStatement update -> List.of(update.tableName());
            case DeleteStatement delete -> List.of(delete.tableName());
            case SelectStatement select -> List.of(select.tableName());
            case ReindexStatement reindex -> List.of(reindex.source(), reindex.dest());
            default -> throw new IllegalArgumentException(kind(statement) + " does not act on an entity");
        };
    }

    /**
     * Replaces the entity names a statement carries with the entities' indices.
     */
    private static Statement address(Statement statement, Function<String, EntityDescriptor> entity) {
        return switch (statement) {
            case InsertStatement insert -> new InsertStatement(entity.apply(insert.tableName()).itemIndex(), insert.columns(),
                                                               insert.values(), insert.refresh(), insert.routing(), insert.documentId());
            case UpdateStatement update -> new UpdateStatement(entity.apply(update.tableName()).itemIndex(), update.assignments(),
                                                               update.whereClause(), update.refresh());
            case DeleteStatement delete -> new DeleteStatement(entity.apply(delete.tableName()).itemIndex(), delete.whereClause(),
                                                               delete.refresh());
            case SelectStatement select -> new SelectStatement(entity.apply(select.tableName()).itemIndex(), select.columns(),
                                                               select.whereClause(), select.orderBy(), select.limit());
            case ReindexStatement reindex -> new ReindexStatement(entity.apply(reindex.source()).itemIndex(),
                                                                  entity.apply(reindex.dest()).itemIndex(),
                                                                  reindex.conflicts(), reindex.maxDocs(), reindex.slices(),
                                                                  reindex.size(), reindex.sourceFields(), reindex.query(),
                                                                  reindex.script(), reindex.waitForReindex(), reindex.skipIfNoSource());
            default -> throw new IllegalArgumentException(kind(statement) + " does not act on an entity");
        };
    }

    /**
     * A migration's rows are literal, so an INSERT takes its identity from the row when it is resolved.
     */
    private InsertStatement identifyFromRow(InsertStatement insert, EntityDescriptor entity) {
        String tenantId = entity.multiTenancyType() == MultiTenancyType.SHARED
                ? rowValue(insert, entity, tenantField(entity), Map.of()) : null;
        return identify(insert, entity, tenantId, rowValue(insert, entity, entity.idFieldName(), Map.of()));
    }

    /**
     * Addresses an INSERT at the entity's index with the document id and routing the entity service gives a row it
     * saves, so the row is reachable through the entity's repository afterwards.
     *
     * @param tenantId the row's tenant, or null for an entity that is not SHARED
     * @param id       the row's id
     */
    private static InsertStatement identify(InsertStatement insert, EntityDescriptor entity, String tenantId, String id) {
        Validate.isTrue(insert.routing() == null && insert.documentId() == null,
                        "INSERT INTO %s: ROUTING and DOCUMENT_ID are derived from the row's fields", entity.name());
        return new InsertStatement(entity.itemIndex(), insert.columns(), insert.values(), insert.refresh(), tenantId,
                                   DomainUtil.createEntityDocumentId(entity.multiTenancyType(), tenantId, id));
    }

    /**
     * Returns the row with its tenant field holding the tenant; a row that names another tenant is refused.
     */
    private InsertStatement withTenant(InsertStatement insert, EntityDescriptor entity, String tenantId, Map<String, Object> parameters) {
        String tenantField = tenantField(entity);
        InsertStatement ret;
        if (insert.columns().contains(tenantField)) {
            String named = rowValue(insert, entity, tenantField, parameters);
            Validate.isTrue(named.equals(tenantId), "INSERT INTO %s names tenant %s, but the participant belongs to %s",
                            entity.name(), named, tenantId);
            ret = insert;
        } else {
            List<String> columns = new ArrayList<>(insert.columns());
            columns.add(tenantField);
            List<Object> values = new ArrayList<>(insert.values());
            values.add(tenantId);
            ret = new InsertStatement(insert.tableName(), columns, values, insert.refresh(), insert.routing(), insert.documentId());
        }
        return ret;
    }

    /**
     * The value an INSERT gives a column, bound against the parameters, which the row has to carry as a non-blank string.
     */
    private static String rowValue(InsertStatement insert, EntityDescriptor entity, String column, Map<String, Object> parameters) {
        Validate.isTrue(insert.columns().size() == insert.values().size(),
                        "INSERT INTO %s lists %d columns and %d values", entity.name(), insert.columns().size(), insert.values().size());
        int position = insert.columns().indexOf(column);
        Validate.isTrue(position >= 0, "INSERT INTO %s must list the %s column", entity.name(), column);
        Object value = ParameterUtils.bind(insert.values().get(position), parameters);
        Validate.isTrue(value instanceof String text && !text.isBlank(),
                        "INSERT INTO %s: %s must be a non-blank string", entity.name(), column);
        return (String) value;
    }

    /**
     * Narrows a WHERE clause to the tenant's documents.
     *
     * @param tenantId the tenant, or null for an entity that is not SHARED, whose clause is returned as is
     */
    private WhereClause confine(WhereClause whereClause, EntityDescriptor entity, String tenantId) {
        WhereClause ret;
        if (tenantId == null) {
            ret = whereClause;
        } else {
            // quoted as the grammar writes a string literal, which is how QueryBuilder tells it from a number
            ret = new WhereClause.AndClause(whereClause, new WhereClause.Condition(tenantField(entity), "==", "'" + tenantId + "'"));
        }
        return ret;
    }

    /**
     * The field holding an item's tenant on a SHARED entity: the entity's own tenant id field, or else the platform's.
     */
    private String tenantField(EntityDescriptor entity) {
        Validate.isTrue(entity.multiTenancyType() == MultiTenancyType.SHARED, "Entity %s has no tenant field", entity.name());
        return entity.isMultiTenantSelectionEnabled() ? entity.tenantIdFieldName() : domainPersistenceProperties.getTenantIdFieldName();
    }

    private static String kind(Statement statement) {
        return statement.getClass().getSimpleName().replace("Statement", "");
    }
}
