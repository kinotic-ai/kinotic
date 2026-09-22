package org.kinotic.auth.casbin;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import org.kinotic.auth.compilers.CasbinCompiler;
import org.kinotic.auth.engines.PreparsedEngine;
import org.kinotic.auth.parsers.PolicyExpressionParser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-parsed entry point for the AviatorScript path {@link CasbinAuthorizationService} uses, so the
 * comparison harness can time the evaluator without the request JSON parse. Lives in this package
 * to share {@link LikeFunction}.
 */
public final class PreparsedAviatorEngine implements PreparsedEngine {

    private final AviatorEvaluatorInstance aviator = AviatorEvaluator.newInstance();
    private final Map<String, Expression> compiled = new ConcurrentHashMap<>();

    public PreparsedAviatorEngine() {
        aviator.addFunction(new LikeFunction());
    }

    public void registerPolicy(String action, String expression) {
        compiled.put(action, aviator.compile(CasbinCompiler.compile(PolicyExpressionParser.parse(expression)), true));
    }

    @Override
    public boolean isAllowed(String action, Map<String, Object> subject, Map<String, Object> arguments) {
        Expression expression = compiled.get(action);
        if (expression == null) {
            throw new IllegalStateException("No policy registered for action: " + action);
        }
        boolean ret;
        try {
            ret = Boolean.TRUE.equals(expression.execute(Map.<String, Object>of("r", Map.<String, Object>of("sub", subject, "obj", arguments))));
        } catch (RuntimeException evaluationError) {
            ret = false;
        }
        return ret;
    }
}
