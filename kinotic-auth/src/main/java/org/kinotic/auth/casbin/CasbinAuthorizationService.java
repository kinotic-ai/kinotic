package org.kinotic.auth.casbin;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import org.kinotic.auth.api.engine.AuthorizationEngine;
import org.kinotic.auth.api.engine.AuthorizationRequest;
import org.kinotic.auth.compilers.CasbinCompiler;
import org.kinotic.auth.parsers.PolicyExpressionParser;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure-JVM ABAC {@link AuthorizationEngine} that evaluates AviatorScript matcher conditions — the
 * matcher language Casbin uses for ABAC — with no native library.
 * <p>
 * Each registered ABAC expression is compiled to an AviatorScript condition (via {@link CasbinCompiler})
 * and pre-compiled to a cached {@link Expression} keyed by action. Evaluation parses the request's
 * participant attributes and method arguments into the {@code r.sub.*} / {@code r.obj.*} maps the
 * condition reads, then executes the pre-compiled expression in-process.
 * <p>
 * jCasbin's {@code Enforcer} is intentionally not on the evaluation path: its {@code eval(p.cond)}
 * matcher recompiles the AviatorScript expression on every call, which dominates latency. Pre-compiling
 * once per action and executing the cached expression is the production-grade equivalent.
 */
public class CasbinAuthorizationService implements AuthorizationEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AviatorEvaluatorInstance aviator = AviatorEvaluator.newInstance();

    /**
     * Pre-compiled AviatorScript condition per action.
     */
    private final Map<String, Expression> compiledByAction = new ConcurrentHashMap<>();

    public CasbinAuthorizationService() {
        aviator.addFunction(new LikeFunction());
    }

    @Override
    public void registerPolicy(String action, String expression) {
        try {
            var ast = PolicyExpressionParser.parse(expression);
            String condition = CasbinCompiler.compile(ast);
            compiledByAction.put(action, aviator.compile(condition, true));
        } catch (Exception e) {
            throw new CasbinPolicyRegistrationException(
                    "Failed to register jCasbin policy for action '" + action + "': " + expression, e);
        }
    }

    @Override
    public boolean isAuthorized(AuthorizationRequest request) {
        Expression compiled = compiledByAction.get(request.action());
        if (compiled == null) {
            throw new CasbinAuthorizationException("No policy registered for action: " + request.action());
        }

        Map<String, Object> subject;
        Map<String, Object> object;
        try {
            subject = parseObject(request.principalAttributesJson());
            object = buildNamedArguments(request.argumentsJson(), request.parameterNames());
        } catch (Exception e) {
            throw new CasbinAuthorizationException("Failed to parse request for action: " + request.action(), e);
        }

        Map<String, Object> env = Map.of("r", Map.of("sub", subject, "obj", object));
        try {
            return Boolean.TRUE.equals(compiled.execute(env));
        } catch (RuntimeException evaluationError) {
            // A missing attribute or type mismatch denies, matching Cedar's fail-closed behavior.
            return false;
        }
    }

    @Override
    public boolean hasPolicy(String action) {
        return compiledByAction.containsKey(action);
    }

    @Override
    public void removePolicy(String action) {
        compiledByAction.remove(action);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(json, Map.class);
    }

    /**
     * Maps the positional JSON argument array onto a named object using the parameter names,
     * producing the {@code r.obj} the condition reads (e.g. {@code [{"amount":25000}]} with names
     * {@code ["order"]} becomes {@code {"order": {"amount": 25000}}}).
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildNamedArguments(String argumentsJson, List<String> parameterNames) {
        List<Object> arguments = MAPPER.readValue(argumentsJson, List.class);
        Map<String, Object> named = new HashMap<>();
        for (int i = 0; i < arguments.size() && i < parameterNames.size(); i++) {
            named.put(parameterNames.get(i), arguments.get(i));
        }
        return named;
    }
}
