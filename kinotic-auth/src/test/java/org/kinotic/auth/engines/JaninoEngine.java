package org.kinotic.auth.engines;

import org.codehaus.janino.ExpressionEvaluator;
import org.kinotic.auth.api.engine.AuthorizationEngine;
import org.kinotic.auth.api.engine.AuthorizationRequest;
import org.kinotic.auth.parsers.PolicyExpressionParser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Benchmark engine that compiles each action's condition to a JVM class with Janino and evaluates
 * it as plain Java.
 */
public final class JaninoEngine implements AuthorizationEngine, PreparsedEngine {

    private final Map<String, PolicyPredicate> predicates = new ConcurrentHashMap<>();

    @Override
    public void registerPolicy(String action, String expression) {
        try {
            String source = JaninoCompiler.compile(PolicyExpressionParser.parse(expression));
            ExpressionEvaluator evaluator = new ExpressionEvaluator();
            evaluator.setParentClassLoader(PolicyFunctions.class.getClassLoader());
            predicates.put(action, evaluator.createFastEvaluator(source, PolicyPredicate.class, new String[] {"r"}));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to register Janino policy for action '" + action + "': " + expression, e);
        }
    }

    @Override
    public boolean isAuthorized(AuthorizationRequest request) {
        return isAllowed(request.action(), RequestJson.subject(request), RequestJson.arguments(request));
    }

    @Override
    public boolean isAllowed(String action, Map<String, Object> subject, Map<String, Object> arguments) {
        PolicyPredicate predicate = predicates.get(action);
        if (predicate == null) {
            throw new IllegalStateException("No policy registered for action: " + action);
        }
        boolean ret;
        try {
            ret = predicate.test(Map.<String, Object>of("sub", subject, "obj", arguments));
        } catch (RuntimeException evaluationError) {
            ret = false;
        }
        return ret;
    }

    @Override
    public boolean hasPolicy(String action) {
        return predicates.containsKey(action);
    }

    @Override
    public void removePolicy(String action) {
        predicates.remove(action);
    }
}
