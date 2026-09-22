package org.kinotic.auth.engines;

import jakarta.el.ELContext;
import jakarta.el.ELManager;
import jakarta.el.ELProcessor;
import jakarta.el.ExpressionFactory;
import jakarta.el.ValueExpression;
import org.kinotic.auth.api.engine.AuthorizationEngine;
import org.kinotic.auth.api.engine.AuthorizationRequest;
import org.kinotic.auth.parsers.PolicyExpressionParser;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Benchmark engine evaluating each action's condition with Jakarta Expression Language (Tomcat's
 * implementation), with every operator bound to a {@link PolicyFunctions} EL function.
 */
public final class ElEngine implements AuthorizationEngine, PreparsedEngine {

    private final ELProcessor processor = new ELProcessor();
    private final ELContext context;
    private final ExpressionFactory factory;
    private final Map<String, ValueExpression> expressions = new ConcurrentHashMap<>();

    public ElEngine() {
        try {
            for (Method method : PolicyFunctions.class.getMethods()) {
                String elName = ElCompiler.EL_NAMES.get(method.getName());
                if (elName != null && method.getDeclaringClass() == PolicyFunctions.class
                        && Modifier.isStatic(method.getModifiers())) {
                    processor.defineFunction("k", elName, method);
                }
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Failed to bind EL functions", e);
        }
        this.context = processor.getELManager().getELContext();
        this.factory = ELManager.getExpressionFactory();
    }

    @Override
    public void registerPolicy(String action, String expression) {
        try {
            String el = ElCompiler.compile(PolicyExpressionParser.parse(expression));
            expressions.put(action, factory.createValueExpression(context, el, Boolean.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to register EL policy for action '" + action + "': " + expression, e);
        }
    }

    @Override
    public boolean isAuthorized(AuthorizationRequest request) {
        return isAllowed(request.action(), RequestJson.subject(request), RequestJson.arguments(request));
    }

    @Override
    public boolean isAllowed(String action, Map<String, Object> subject, Map<String, Object> arguments) {
        ValueExpression expression = expressions.get(action);
        if (expression == null) {
            throw new IllegalStateException("No policy registered for action: " + action);
        }
        boolean ret;
        try {
            processor.defineBean("r", Map.<String, Object>of("sub", subject, "obj", arguments));
            ret = Boolean.TRUE.equals(expression.getValue(context));
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
