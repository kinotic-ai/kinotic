package org.kinotic.auth.engines;

import org.kinotic.auth.api.engine.AuthorizationEngine;
import org.kinotic.auth.api.engine.AuthorizationRequest;
import org.kinotic.auth.parsers.PolicyExpressionParser;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Benchmark engine evaluating each action's condition with Spring's SpEL in compiled mode, on a
 * restricted evaluation context that permits property access, indexing and instance methods but no
 * type references, constructors or bean references.
 */
public final class SpelEngine implements AuthorizationEngine, PreparsedEngine {

    private final SpelExpressionParser parser = new SpelExpressionParser(
            new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, SpelEngine.class.getClassLoader()));
    private final EvaluationContext context = SimpleEvaluationContext.forPropertyAccessors(new MapAccessor())
            .withInstanceMethods()
            .build();
    private final Map<String, SpelExpression> expressions = new ConcurrentHashMap<>();

    @Override
    public void registerPolicy(String action, String expression) {
        try {
            expressions.put(action, parser.parseRaw(SpelCompiler.compile(PolicyExpressionParser.parse(expression))));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to register SpEL policy for action '" + action + "': " + expression, e);
        }
    }

    @Override
    public boolean isAuthorized(AuthorizationRequest request) {
        return isAllowed(request.action(), RequestJson.subject(request), RequestJson.arguments(request));
    }

    @Override
    public boolean isAllowed(String action, Map<String, Object> subject, Map<String, Object> arguments) {
        SpelExpression expression = expressions.get(action);
        if (expression == null) {
            throw new IllegalStateException("No policy registered for action: " + action);
        }
        boolean ret;
        try {
            ret = Boolean.TRUE.equals(expression.getValue(context, Map.<String, Object>of("sub", subject, "obj", arguments), Boolean.class));
        } catch (RuntimeException evaluationError) {
            ret = false;
        }
        return ret;
    }

    /**
     * Reports, per action, whether SpEL compiled the expression to bytecode. Meaningful only after
     * each expression has been evaluated at least once, since compilation needs observed types.
     */
    public Map<String, Boolean> compileAll() {
        Map<String, Boolean> ret = new LinkedHashMap<>();
        expressions.forEach((action, expression) -> ret.put(action, expression.compileExpression()));
        return ret;
    }

    @Override
    public boolean hasPolicy(String action) {
        return expressions.containsKey(action);
    }

    @Override
    public void removePolicy(String action) {
        expressions.remove(action);
    }
}
