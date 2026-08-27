package org.kinotic.sql.executor.executors;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.ScriptSource;
import co.elastic.clients.elasticsearch.core.UpdateByQueryResponse;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import org.kinotic.sql.domain.BinaryExpression;
import org.kinotic.sql.domain.Expression;
import org.kinotic.sql.domain.LiteralExpression;
import org.kinotic.sql.domain.NamedParameter;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.UpdateStatement;
import org.kinotic.sql.executor.ParameterUtils;
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

    private static final String ASSIGNMENT = """
            ctx._source.%1$s = params.%1$s;
            """;

    private static final String BINARY_ASSIGNMENT = """
            ctx._source.%1$s = ctx._source.%2$s %3$s %4$s;
            """;

    // A null clears the field, leaving the document as though it had never carried one
    private static final String CLEARING_ASSIGNMENT = """
            ctx._source.remove("%1$s");
            """;

    // An object is merged into the stored one rather than replacing it, so sub-fields the statement
    // does not mention survive. A stored scalar, array, or missing field is turned into an empty object
    // first so that the merge, and the removal a null in it asks for, applies at every depth.
    private static final String MERGE_ASSIGNMENT = """
            if (params.%1$s instanceof Map) {
              if (!(ctx._source.%1$s instanceof Map)) {
                ctx._source.%1$s = [:];
              }
              mergeTargets.add(ctx._source.%1$s);
              mergeSources.add(params.%1$s);
            } else {
              ctx._source.%1$s = params.%1$s;
            }
            """;

    private static final String MERGE_QUEUE = """
            List mergeTargets = new ArrayList();
            List mergeSources = new ArrayList();
            """;

    // Merges every queued pair, appending nested objects to the queue as it finds them, which is what
    // makes the merge recursive without a recursive function: size() is re-read on each pass, so pairs
    // appended during the loop are picked up by it.
    private static final String MERGE_DRAIN = """
            for (int i = 0; i < mergeTargets.size(); i++) {
              Map target = (Map) mergeTargets.get(i);
              Map source = (Map) mergeSources.get(i);
              for (def key : source.keySet()) {
                def incoming = source.get(key);
                if (incoming == null) {
                  target.remove(key);
                } else if (incoming instanceof Map) {
                  def current = target.get(key);
                  if (!(current instanceof Map)) {
                    current = [:];
                    target.put(key, current);
                  }
                  mergeTargets.add(current);
                  mergeSources.add(incoming);
                } else {
                  target.put(key, incoming);
                }
              }
            }
            """;

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
        boolean merges = assignments.values().stream().anyMatch(UpdateStatementExecutor::mergesIntoStoredObject);
        if (merges) {
            script.append(MERGE_QUEUE);
        }
        assignments.forEach((field, expr) -> {
            if (expr instanceof BinaryExpression(String left, String binaryOperator, String rightOperand)) {
                String operator = switch (binaryOperator) {
                    case "+" -> "+";
                    case "-" -> "-";
                    case "*" -> "*";
                    case "/" -> "/";
                    case "==" -> "=="; // Not typically used in SET, but included
                    default -> throw new IllegalStateException("Unsupported operator: " + binaryOperator);
                };
                String right = ParameterUtils.isReference(rightOperand) ? "params." + field : rightOperand;
                script.append(BINARY_ASSIGNMENT.formatted(field, left, operator, right));
            } else if (clearsStoredValue(expr)) {
                script.append(CLEARING_ASSIGNMENT.formatted(field));
            } else if (mergesIntoStoredObject(expr)) {
                script.append(MERGE_ASSIGNMENT.formatted(field));
            } else {
                // Literals and parameters alike are passed as script params, so an object or array
                // value crosses as JSON instead of being rendered into the script source
                script.append(ASSIGNMENT.formatted(field));
            }
        });
        if (merges) {
            script.append(MERGE_DRAIN);
        }
        return ScriptSource.of(ssb -> ssb.scriptString(script.toString()));
    }

    /**
     * Whether the field is assigned a null literal, which removes it rather than storing a value.
     */
    private static boolean clearsStoredValue(Expression expression) {
        return expression instanceof LiteralExpression(Object value) && value == null;
    }

    /**
     * Whether the value assigned to a field can be an object that merges into the stored one.
     * A parameter qualifies because what it is bound to is only known when the statement runs,
     * which is why the generated script tests both sides before merging.
     */
    private static boolean mergesIntoStoredObject(Expression expression) {
        return expression instanceof NamedParameter
                || (expression instanceof LiteralExpression(Object value) && value instanceof Map);
    }

    private Map<String, Object> buildScriptParams(Map<String, Expression> assignments, Map<String, Object> parameters) {
        Map<String, Object> params = new HashMap<>();
        assignments.forEach((field, expr) -> {
            if (expr instanceof LiteralExpression(Object value)) {
                if (value != null) { // a null is written into the script itself, not passed as a param
                    params.put(field, ParameterUtils.bind(value, parameters));
                }
            } else if (expr instanceof NamedParameter(String name)) {
                params.put(field, ParameterUtils.resolve(name, parameters));
            } else if (expr instanceof BinaryExpression binExpr && ParameterUtils.isReference(binExpr.right())) {
                params.put(field, ParameterUtils.resolve(ParameterUtils.nameOf(binExpr.right()), parameters));
            }
        });
        return params;
    }

    private Map<String, JsonData> convertToJsonDataMap(Map<String, Object> params) {
        Map<String, JsonData> jsonDataParams = new HashMap<>();
        params.forEach((key, value) -> jsonDataParams.put(key, JsonData.of(value)));
        return jsonDataParams;
    }
}
