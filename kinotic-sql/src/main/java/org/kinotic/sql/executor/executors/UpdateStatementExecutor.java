package org.kinotic.sql.executor.executors;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.ScriptSource;
import co.elastic.clients.elasticsearch.core.UpdateByQueryResponse;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import org.kinotic.sql.domain.BinaryExpression;
import org.kinotic.sql.domain.Expression;
import org.kinotic.sql.domain.LiteralExpression;
import org.kinotic.sql.domain.ParameterExpression;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.UpdateStatement;
import org.kinotic.sql.executor.QueryBuilder;
import org.kinotic.sql.executor.StatementExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Executes UPDATE statements against Elasticsearch.
 * Applies SET assignments and evaluates WHERE clauses with comparison operators.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
@Component
@RequiredArgsConstructor
public class UpdateStatementExecutor implements StatementExecutor<UpdateStatement, Long> {
    private final ElasticsearchAsyncClient client;

    @Override
    public boolean supports(Statement statement) {
        return statement instanceof UpdateStatement;
    }

    @Override
    public CompletableFuture<Long> executeMigration(UpdateStatement statement) {
        return executeQuery(statement, null);
    }

    @Override
    public CompletableFuture<Long> executeQuery(UpdateStatement statement, Map<String, Object> parameters) {
        ScriptSource scriptSource = buildScript(statement.assignments());
        Map<String, Object> params = buildScriptParams(statement.assignments(), parameters);
        Map<String, JsonData> scriptParams = convertToJsonDataMap(params);

        return client.updateByQuery(u -> u
                .index(statement.tableName())
                .query(QueryBuilder.buildQuery(statement.whereClause(), parameters))
                .script(s -> s.source(scriptSource).params(scriptParams))
                .refresh(statement.refresh())
        ).thenApply(UpdateByQueryResponse::updated);
    }

    private ScriptSource buildScript(Map<String, Expression> assignments) {
        StringBuilder script = new StringBuilder();
        assignments.forEach((field, expr) -> {
            if (expr instanceof BinaryExpression binExpr) {
                String operator = switch (binExpr.operator()) {
                    case "+" -> "+";
                    case "-" -> "-";
                    case "*" -> "*";
                    case "/" -> "/";
                    case "==" -> "=="; // Not typically used in SET, but included
                    default -> throw new IllegalStateException("Unsupported operator: " + binExpr.operator());
                };
                String right = "?".equals(binExpr.right()) ? "params." + field : binExpr.right();
                script.append("ctx._source.").append(field).append(" = ctx._source.").append(binExpr.left())
                      .append(" ").append(operator).append(" ").append(right).append(";");
            } else {
                // Literals and parameters alike are passed as script params, so an object or array
                // value crosses as JSON instead of being rendered into the script source
                script.append("ctx._source.").append(field).append(" = params.").append(field).append(";");
            }
        });
        return ScriptSource.of(ssb -> ssb.scriptString(script.toString()));
    }

    private Map<String, Object> buildScriptParams(Map<String, Expression> assignments, Map<String, Object> parameters) {
        Map<String, Object> params = new HashMap<>();
        assignments.forEach((field, expr) -> {
            if (expr instanceof LiteralExpression literal) {
                params.put(field, literal.value());
            } else if (expr instanceof ParameterExpression) {
                params.put(field, resolveParameter(field, parameters));
            } else if (expr instanceof BinaryExpression binExpr && "?".equals(binExpr.right())) {
                params.put(field, resolveParameter(field, parameters));
            }
        });
        return params;
    }

    private Object resolveParameter(String field, Map<String, Object> parameters) {
        if (parameters == null) {
            throw new IllegalStateException("Parameterized assignment not supported without parameters");
        }
        Object value = parameters.get(field);
        if (value == null) {
            throw new IllegalArgumentException("Missing parameter for " + field);
        }
        return value;
    }

    private Map<String, JsonData> convertToJsonDataMap(Map<String, Object> params) {
        Map<String, JsonData> jsonDataParams = new HashMap<>();
        params.forEach((key, value) -> jsonDataParams.put(key, JsonData.of(value)));
        return jsonDataParams;
    }
}
