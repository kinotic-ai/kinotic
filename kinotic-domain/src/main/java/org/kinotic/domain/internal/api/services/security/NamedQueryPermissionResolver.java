package org.kinotic.domain.internal.api.services.security;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.domain.api.cache.CacheEvictionEvent;
import org.kinotic.domain.api.model.persistence.NamedQueriesDefinition;
import org.kinotic.domain.api.model.persistence.idl.decorators.QueryDecorator;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.api.repositories.NamedQueriesDefinitionRepository;
import org.kinotic.domain.api.services.security.RequestPermissionResolver;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzPermissionChoice;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.sql.domain.statements.DeleteStatement;
import org.kinotic.sql.domain.statements.InsertStatement;
import org.kinotic.sql.domain.statements.UpdateStatement;
import org.kinotic.sql.parsers.MigrationParser;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides a named query's permission from its statement: {@code can_edit} for an INSERT, which replaces a row of
 * the same id, and for an UPDATE, {@code can_delete} for a DELETE and {@code can_read} for any other, on the entity definition the query
 * is defined for. A definition or query that does not exist decides nothing.
 */
@Component
@RequiredArgsConstructor
public class NamedQueryPermissionResolver implements RequestPermissionResolver {

    private static final AuthzPermissionChoice READ = new AuthzPermissionChoice(AuthzConstants.CAN_READ, "the query reads rows");
    private static final AuthzPermissionChoice INSERT = new AuthzPermissionChoice(AuthzConstants.CAN_EDIT, "the query is an INSERT");
    private static final AuthzPermissionChoice EDIT = new AuthzPermissionChoice(AuthzConstants.CAN_EDIT, "the query is an UPDATE");
    private static final AuthzPermissionChoice DELETE = new AuthzPermissionChoice(AuthzConstants.CAN_DELETE, "the query is a DELETE");
    // an INSERT needs the permission an UPDATE does, with its own reason
    private static final List<AuthzPermissionChoice> POSSIBLE_PERMISSIONS = List.of(READ, EDIT, DELETE);
    private static final Duration RETENTION = Duration.ofMinutes(5);
    private static final int CAPACITY = 10_000;

    private final EntityDefinitionRepository entityDefinitionRepository;
    private final NamedQueriesDefinitionRepository namedQueriesDefinitionRepository;
    private final MigrationParser migrationParser;
    // the permission of each query of a definition by query name, keyed by the organization and the definition
    private final AsyncCache<String, Map<String, AuthzPermissionChoice>> permissions = Caffeine.newBuilder()
                                                                                              .maximumSize(CAPACITY)
                                                                                              .expireAfterWrite(RETENTION)
                                                                                              .buildAsync();

    @Override
    public String name() {
        return AuthzConstants.NAMED_QUERY_PERMISSION_RESOLVER;
    }

    @Override
    public List<String> parameters() {
        return List.of("entityDefinitionId", "queryName");
    }

    @Override
    public List<AuthzPermissionChoice> possiblePermissions() {
        return POSSIBLE_PERMISSIONS;
    }

    @Override
    public Future<AuthzPermissionChoice> resolve(ScopedParticipant participant, List<String> arguments) {
        String organizationId = participant.getScope().organizationId();
        String entityDefinitionId = arguments.get(0);
        String queryName = arguments.get(1);
        // a system user or machine has no organization, so no entity definition of its own
        if (organizationId == null) {
            return Future.failedFuture(new IllegalArgumentException("The caller's scope names no organization holding entity definition "
                                                                            + entityDefinitionId));
        }
        return KinoticUtil.toFuture(permissions.get(organizationId + "|" + entityDefinitionId,
                                                    (key, executor) -> load(organizationId, entityDefinitionId).toCompletionStage()
                                                                                                                .toCompletableFuture()))
                          .map(queries -> {
                              AuthzPermissionChoice ret = queries.get(queryName);
                              if (ret == null) {
                                  throw new IllegalArgumentException("The entity definition " + entityDefinitionId + " has no named query " + queryName);
                              }
                              return ret;
                          });
    }

    /**
     * Drops the permissions kept for an organization whose named queries or entity definitions changed.
     */
    @EventListener
    public void handleCacheEviction(CacheEvictionEvent event) {
        String prefix = event.getApplicationKey().organizationId() + "|";
        permissions.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    private Future<Map<String, AuthzPermissionChoice>> load(String organizationId, String entityDefinitionId) {
        return entityDefinitionRepository.findById(entityDefinitionId, organizationId)
                                         .compose(definition -> definition == null
                                                 ? Future.succeededFuture(null)
                                                 : namedQueriesDefinitionRepository.findByApplicationAndEntityDefinition(definition.getApplicationId(),
                                                                                                                          definition.getName(),
                                                                                                                          organizationId))
                                         .map(this::permissionsOf);
    }

    private Map<String, AuthzPermissionChoice> permissionsOf(NamedQueriesDefinition namedQueries) {
        Map<String, AuthzPermissionChoice> ret = new HashMap<>();
        if (namedQueries != null && namedQueries.getNamedQueries() != null) {
            for (FunctionDefinition query : namedQueries.getNamedQueries()) {
                ret.put(query.getName(), permissionOf(query));
            }
        }
        return Map.copyOf(ret);
    }

    // The statement is parsed as DefaultQueryExecutorFactory parses it before running it, so a write is never
    // decided to be a read. A query that does not parse never runs, and is checked as a read so a caller
    // who may read learns why it fails
    private AuthzPermissionChoice permissionOf(FunctionDefinition query) {
        QueryDecorator decorator = query.findDecorator(QueryDecorator.class);
        AuthzPermissionChoice ret = READ;
        if (decorator != null && decorator.getStatements() != null) {
            try {
                ret = switch (migrationParser.parseStatement(decorator.getStatements(), "Named query " + query.getName())) {
                    case InsertStatement _ -> INSERT;
                    case UpdateStatement _ -> EDIT;
                    case DeleteStatement _ -> DELETE;
                    default -> READ;
                };
            } catch (IllegalArgumentException e) {
                ret = READ;
            }
        }
        return ret;
    }
}
