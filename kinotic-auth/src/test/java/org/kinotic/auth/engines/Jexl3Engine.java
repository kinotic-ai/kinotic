package org.kinotic.auth.engines;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.kinotic.auth.api.engine.AuthorizationEngine;
import org.kinotic.auth.api.engine.AuthorizationRequest;
import org.kinotic.auth.parsers.PolicyExpressionParser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Benchmark engine evaluating each action's condition with Apache Commons JEXL 3 under its
 * restricted permissions, in strict mode so null operands fail evaluation instead of coercing.
 */
public final class Jexl3Engine implements AuthorizationEngine, PreparsedEngine {

    private final JexlEngine jexl = new JexlBuilder()
            .permissions(JexlPermissions.RESTRICTED)
            .strict(true)
            .silent(false)
            .cache(256)
            .create();
    private final Map<String, JexlExpression> expressions = new ConcurrentHashMap<>();

    @Override
    public void registerPolicy(String action, String expression) {
        try {
            expressions.put(action, jexl.createExpression(JexlCompiler.compile(PolicyExpressionParser.parse(expression))));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to register JEXL policy for action '" + action + "': " + expression, e);
        }
    }

    @Override
    public boolean isAuthorized(AuthorizationRequest request) {
        return isAllowed(request.action(), RequestJson.subject(request), RequestJson.arguments(request));
    }

    @Override
    public boolean isAllowed(String action, Map<String, Object> subject, Map<String, Object> arguments) {
        JexlExpression expression = expressions.get(action);
        if (expression == null) {
            throw new IllegalStateException("No policy registered for action: " + action);
        }
        JexlContext context = new MapContext(Map.<String, Object>of("r", Map.<String, Object>of("sub", subject, "obj", arguments)));
        boolean ret;
        try {
            ret = Boolean.TRUE.equals(expression.evaluate(context));
        } catch (RuntimeException evaluationError) {
            ret = false;
        }
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
