package org.kinotic.persistence.internal.api.services.sql;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.NotImplementedException;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.kinotic.persistence.api.domain.NamedQueriesDefinition;
import org.kinotic.persistence.api.domain.NamedQueryOperation;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.domain.idl.decorators.QueryDecorator;
import org.kinotic.persistence.api.services.security.AuthorizationService;
import org.kinotic.persistence.api.services.security.AuthorizationServiceFactory;
import org.kinotic.persistence.internal.api.services.sql.elasticsearch.ElasticVertxClient;
import org.kinotic.persistence.internal.api.services.sql.executors.AggregateQueryExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.ParameterProcessorExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.PreAuthorizationExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.QueryExecutor;
import org.kinotic.persistence.internal.utils.QueryUtils;
import org.springframework.stereotype.Component;

/**
 * Created by Navíd Mitchell 🤪 on 4/28/24.
 */
@Component
@RequiredArgsConstructor
public class DefaultQueryExecutorFactory implements QueryExecutorFactory {

    private final ElasticVertxClient elasticVertxClient;
    private final StructuresProperties structuresProperties;
    private final AuthorizationServiceFactory authorizationServiceFactory;

    public QueryExecutor createQueryExecutor(Structure structure,
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

        String[] statements = queryDecorator.getStatements().split(";");
        if(statements.length == 1){
            QueryExecutor queryExecutor = createQueryExecutorForStatement(structure, statements[0], namedQuery);
            AuthorizationService<NamedQueryOperation> authorizationService =
                    authorizationServiceFactory.createNamedQueryAuthorizationService(namedQuery).join();
            return new ParameterProcessorExecutor(structure,
                    namedQuery,
                    new PreAuthorizationExecutor(authorizationService, queryExecutor));
        }else{
            throw new IllegalArgumentException("Multiple statements not supported yet");
        }
    }

    private QueryExecutor createQueryExecutorForStatement(Structure structure,
                                                          String statement,
                                                          FunctionDefinition namedQueryDefinition) {
        // naive approach to how we handle these queries, this will be improved as we do more R&D on advanced approaches
        SqlQueryType queryType = QueryUtils.determineQueryType(statement);
        return switch (queryType) {
            case AGGREGATE -> new AggregateQueryExecutor(structure,
                                                         elasticVertxClient,
                                                         statement,
                                                         structuresProperties);
            case DELETE -> throw new NotImplementedException("Delete not supported yet");
            case INSERT -> throw new NotImplementedException("Insert not supported yet");
            case SELECT -> throw new NotImplementedException("Select without aggregate not supported yet");
            case UPDATE -> throw new NotImplementedException("Update not supported yet");
        };
    }
}
