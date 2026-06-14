package org.kinotic.auth.casbin;

import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.model.Model;
import org.kinotic.auth.api.engine.AuthorizationEngine;
import org.kinotic.auth.api.engine.AuthorizationRequest;
import org.kinotic.auth.compilers.CasbinCompiler;
import org.kinotic.auth.parsers.PolicyExpressionParser;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * jCasbin-backed {@link AuthorizationEngine}: a pure-JVM ABAC engine that evaluates policies with
 * AviatorScript matchers, requiring no native library.
 * <p>
 * Each registered ABAC expression is compiled to an AviatorScript condition and stored as a jCasbin
 * policy keyed by action; the shared model evaluates {@code eval(p.cond)} for the action matching the
 * request. Requests carry participant attributes and method arguments as raw JSON, which are parsed
 * into the maps the matcher reads as {@code r.sub.*} and {@code r.obj.*}.
 */
public class CasbinAuthorizationService implements AuthorizationEngine {

    private static final String MODEL_TEXT = """
            [request_definition]
            r = sub, obj, act

            [policy_definition]
            p = act, cond

            [policy_effect]
            e = some(where (p.eft == allow))

            [matchers]
            m = r.act == p.act && eval(p.cond)
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Enforcer enforcer;

    /**
     * Actions with a registered policy, for O(1) membership checks on the evaluation path.
     */
    private final Set<String> registeredActions = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new CasbinAuthorizationService with an in-memory ABAC enforcer.
     *
     * @throws CasbinInitializationException if the ABAC model cannot be loaded
     */
    public CasbinAuthorizationService() {
        try {
            Model model = new Model();
            model.loadModelFromText(MODEL_TEXT);
            this.enforcer = new Enforcer(model);
            this.enforcer.enableLog(false);
        } catch (Exception e) {
            throw new CasbinInitializationException("Failed to initialize jCasbin enforcer", e);
        }
    }

    @Override
    public void registerPolicy(String action, String expression) {
        try {
            var ast = PolicyExpressionParser.parse(expression);
            String condition = CasbinCompiler.compile(ast);

            // Replace any prior policy so re-registration is idempotent.
            enforcer.removeFilteredPolicy(0, action);
            enforcer.addPolicy(action, condition);
            registeredActions.add(action);

        } catch (Exception e) {
            throw new CasbinPolicyRegistrationException(
                    "Failed to register jCasbin policy for action '" + action + "': " + expression, e);
        }
    }

    @Override
    public boolean isAuthorized(AuthorizationRequest request) {
        if (!registeredActions.contains(request.action())) {
            throw new CasbinAuthorizationException("No policy registered for action: " + request.action());
        }
        try {
            Map<String, Object> subject = parseObject(request.principalAttributesJson());
            Map<String, Object> object = buildNamedArguments(request.argumentsJson(), request.parameterNames());
            return enforcer.enforce(subject, object, request.action());
        } catch (Exception e) {
            throw new CasbinAuthorizationException("jCasbin authorization failed for action: " + request.action(), e);
        }
    }

    @Override
    public boolean hasPolicy(String action) {
        return registeredActions.contains(action);
    }

    @Override
    public void removePolicy(String action) {
        enforcer.removeFilteredPolicy(0, action);
        registeredActions.remove(action);
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
     * producing the {@code r.obj} the matcher reads (e.g. {@code [{"amount":25000}]} with names
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
