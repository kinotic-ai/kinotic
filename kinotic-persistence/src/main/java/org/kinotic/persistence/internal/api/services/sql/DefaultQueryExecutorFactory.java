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
import org.kinotic.persistence.internal.api.hooks.ReadPostProcessor;
import org.kinotic.persistence.internal.api.hooks.ReadPreProcessor;
import org.kinotic.persistence.internal.api.services.sql.elasticsearch.ElasticVertxClient;
import org.kinotic.persistence.internal.api.services.sql.executors.AggregateQueryExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.EntityContextValidationExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.ParameterProcessorExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.QueryExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.SelectQueryExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.StatementQueryExecutor;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.AggregateStatement;
import org.kinotic.sql.domain.statements.DeleteStatement;
import org.kinotic.sql.domain.statements.InsertStatement;
import org.kinotic.sql.domain.statements.SelectStatement;
import org.kinotic.sql.domain.statements.UpdateStatement;
import org.kinotic.sql.executor.ElasticsearchSqlWriter;
import org.kinotic.sql.executor.StatementExecutor;
import org.kinotic.sql.parsers.MigrationParser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 4/28/24.
 */
@Component
@RequiredArgsConstructor
public class DefaultQueryExecutorFactory implements QueryExecutorFactory {

    private final ElasticVertxClient elasticVertxClient;
    private final DomainPersistenceProperties domainPersistenceProperties;
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
        return new ParameterProcessorExecutor(entityDescriptor,
                                              namedQuery,
                                              new EntityContextValidationExecutor(entityDescriptor, queryExecutor));
    }

    private QueryExecutor createQueryExecutorForStatement(EntityDescriptor entityDescriptor,
                                                          String queryName,
                                                          String statements) {
        Statement statement = entityStatementResolver.resolve(parse(statements, queryName), entityDescriptor).getFirst();
        return switch (statement) {
            case SelectStatement select -> new SelectQueryExecutor(entityDescriptor, queryName, select, crudServiceTemplate,
                                                                   readPreProcessor, readPostProcessor);
            case AggregateStatement aggregate -> new AggregateQueryExecutor(entityDescriptor,
                                                                            elasticVertxClient,
                                                                            ElasticsearchSqlWriter.write(aggregate),
                                                                            domainPersistenceProperties);
            case InsertStatement _, UpdateStatement _, DeleteStatement _ -> write(entityDescriptor, statement);
            default -> throw new IllegalArgumentException("Named query " + queryName
                                                                  + ": a named query is a SELECT, INSERT, UPDATE or DELETE, so "
                                                                  + statement.getClass().getSimpleName().replace("Statement", "")
                                                                  + " is not allowed");
        };
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
}
