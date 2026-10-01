package org.kinotic.persistence.internal.api.services.sql;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.config.DomainPersistenceProperties;
import org.kinotic.domain.api.model.persistence.EntityDescriptor;
import org.kinotic.domain.api.model.persistence.NamedQueriesDefinition;
import org.kinotic.domain.api.model.persistence.idl.decorators.QueryDecorator;
import org.kinotic.domain.api.services.EntityStatementResolver;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.NamedQueryOperation;
import org.kinotic.persistence.api.services.security.AuthorizationService;
import org.kinotic.persistence.api.services.security.AuthorizationServiceFactory;
import org.kinotic.persistence.internal.api.hooks.ReadPostProcessor;
import org.kinotic.persistence.internal.api.hooks.ReadPreProcessor;
import org.kinotic.persistence.internal.api.services.sql.elasticsearch.ElasticVertxClient;
import org.kinotic.persistence.internal.api.services.sql.executors.AggregateQueryExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.ParameterProcessorExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.PreAuthorizationExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.QueryExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.SelectQueryExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.StatementQueryExecutor;
import org.kinotic.persistence.internal.utils.QueryUtils;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.DeleteStatement;
import org.kinotic.sql.domain.statements.InsertStatement;
import org.kinotic.sql.domain.statements.SelectStatement;
import org.kinotic.sql.domain.statements.UpdateStatement;
import org.kinotic.sql.executor.StatementExecutor;
import org.kinotic.sql.parsers.MigrationParser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Navíd Mitchell 🤪 on 4/28/24.
 */
@Component
@RequiredArgsConstructor
public class DefaultQueryExecutorFactory implements QueryExecutorFactory {

    // the index an Elasticsearch SQL statement reads, bare or double-quoted
    private static final Pattern FROM_ENTITY = Pattern.compile("(?i)\\bFROM\\s+(\"?)([A-Za-z_][A-Za-z0-9_.-]*)\\1");
    // a single-quoted string is matched whole, so a colon inside one is not a parameter; nor is a `::` cast
    private static final Pattern PARAMETER = Pattern.compile("'(?:[^']|'')*'|(?<!:):([A-Za-z_][A-Za-z0-9_]*)");

    private final ElasticVertxClient elasticVertxClient;
    private final DomainPersistenceProperties domainPersistenceProperties;
    private final AuthorizationServiceFactory authorizationServiceFactory;
    private final MigrationParser migrationParser;
    private final EntityStatementResolver entityStatementResolver;
    private final List<StatementExecutor<?, ?>> statementExecutors;
    private final CrudServiceTemplate crudServiceTemplate;
    private final ReadPreProcessor readPreProcessor;
    private final ReadPostProcessor readPostProcessor;
    private final JsonMapper jsonMapper;

    public QueryExecutor createQueryExecutor(EntityDescriptor entityDescriptor,
                                             String queryName,
                                             NamedQueriesDefinition namedQueriesDefinition){
        FunctionDefinition namedQuery = null;
        for(FunctionDefinition queries : namedQueriesDefinition.getNamedQueries()){
            if(queries.getName().equals(queryName)){
                namedQuery = queries;
                break;
            }
        }
        if(namedQuery == null){
            throw new IllegalArgumentException("No query found with name " + queryName);
        }

        // Sanity check, but should never happen if using the CLI
        QueryDecorator queryDecorator = namedQuery.findDecorator(QueryDecorator.class);
        if(queryDecorator == null
                || queryDecorator.getStatements() == null){
            throw new IllegalArgumentException("No Query defined");
        }

        QueryExecutor queryExecutor = createQueryExecutorForStatement(entityDescriptor, queryName, queryDecorator.getStatements());
        AuthorizationService<NamedQueryOperation> authorizationService =
                authorizationServiceFactory.createNamedQueryAuthorizationService(namedQuery)
                                           .toCompletionStage().toCompletableFuture().join();
        return new ParameterProcessorExecutor(entityDescriptor,
                                              namedQuery,
                                              new PreAuthorizationExecutor(entityDescriptor,
                                                                           authorizationService,
                                                                           queryExecutor));
    }

    private QueryExecutor createQueryExecutorForStatement(EntityDescriptor entityDescriptor,
                                                          String queryName,
                                                          String statements) {
        QueryExecutor ret;
        if(QueryUtils.isAggregate(statements)){
            // an aggregate runs on Elasticsearch SQL as written, apart from the entity it names and its parameters
            ret = new AggregateQueryExecutor(entityDescriptor,
                                             elasticVertxClient,
                                             positional(addressAggregate(statements, entityDescriptor, queryName)),
                                             parameterNames(statements),
                                             domainPersistenceProperties);
        }else{
            Statement statement = entityStatementResolver.resolve(parse(statements, queryName), entityDescriptor).getFirst();
            ret = switch (statement) {
                case SelectStatement select -> new SelectQueryExecutor(entityDescriptor, queryName, select, crudServiceTemplate,
                                                                       readPreProcessor, readPostProcessor);
                case InsertStatement _, UpdateStatement _, DeleteStatement _ -> write(entityDescriptor, statement);
                default -> throw new IllegalArgumentException("Named query " + queryName
                                                                      + ": a named query is a SELECT, INSERT, UPDATE or DELETE, so "
                                                                      + statement.getClass().getSimpleName().replace("Statement", "")
                                                                      + " is not allowed");
            };
        }
        return ret;
    }

    private List<Statement> parse(String statements, String queryName) {
        // a named query is one statement, usually written without the terminator the grammar requires
        String sql = statements.strip();
        List<Statement> ret = migrationParser.parse(sql.endsWith(";") ? sql : sql + ";", queryName).statements();
        Validate.isTrue(ret.size() == 1, "Named query %s must be one statement", queryName);
        return ret;
    }

    @SuppressWarnings("unchecked")
    private QueryExecutor write(EntityDescriptor entityDescriptor, Statement statement) {
        StatementExecutor<Statement, ?> executor = (StatementExecutor<Statement, ?>) statementExecutors
                .stream()
                .filter(candidate -> candidate.supports(statement))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No executor found for statement: " + statement.getClass().getSimpleName()));
        return new StatementQueryExecutor(entityDescriptor, statement, entityStatementResolver, executor, jsonMapper);
    }

    /**
     * Replaces the entity an aggregate names in FROM with the entity's index. The entity must be the one the
     * named query belongs to.
     */
    private static String addressAggregate(String statement, EntityDescriptor entityDescriptor, String queryName) {
        Matcher matcher = FROM_ENTITY.matcher(statement);
        Validate.isTrue(matcher.find(), "Named query %s names no entity in FROM", queryName);
        return matcher.replaceAll(match -> {
            Validate.isTrue(entityDescriptor.name().equalsIgnoreCase(match.group(2)),
                            "A named query of %s acts on %s, not %s", entityDescriptor.name(), entityDescriptor.name(), match.group(2));
            return Matcher.quoteReplacement("FROM \"" + entityDescriptor.itemIndex() + "\"");
        });
    }

    /**
     * The names of an aggregate's {@code :name} parameters, in the order they appear.
     */
    private static List<String> parameterNames(String statement) {
        List<String> ret = new ArrayList<>();
        Matcher matcher = PARAMETER.matcher(statement);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                ret.add(matcher.group(1));
            }
        }
        return ret;
    }

    /**
     * The aggregate with each {@code :name} parameter replaced by the {@code ?} placeholder Elasticsearch SQL takes.
     */
    private static String positional(String statement) {
        return PARAMETER.matcher(statement)
                        .replaceAll(match -> match.group(1) != null ? "?" : Matcher.quoteReplacement(match.group()));
    }
}
