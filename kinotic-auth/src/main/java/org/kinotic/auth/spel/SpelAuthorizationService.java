package org.kinotic.auth.spel;

import org.kinotic.auth.api.engine.AuthorizationEngine;
import org.kinotic.auth.api.engine.AuthorizationRequest;
import org.kinotic.auth.compilers.SpelCompiler;
import org.kinotic.auth.parsers.PolicyExpressionParser;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SpEL-backed {@link AuthorizationEngine}: each action's ABAC expression is compiled once (via
 * {@link SpelCompiler}) and evaluated in-process, with SpEL compiling hot expressions to bytecode.
 * <p>
 * Evaluation runs on a sandboxed context that permits only read-only map navigation, indexing,
 * operators and the {@code #contains}/{@code #like} functions: no method invocation, type
 * references, constructors, bean references or assignment, so a policy can never reach the JVM
 * beyond the request data it is given. A missing attribute or a type mismatch denies.
 */
public class SpelAuthorizationService implements AuthorizationEngine {

    // JSON integers parse as Long so a compiled comparison always sees one numeric type.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_LONG_FOR_INTS)
            .build();

    // MIXED mode compiles an expression after it has been interpreted enough to observe its types,
    // and reverts it to interpretation if compiled code ever meets a different type, rather than
    // failing the request.
    private final SpelExpressionParser parser = new SpelExpressionParser(
            new SpelParserConfiguration(SpelCompilerMode.MIXED, SpelAuthorizationService.class.getClassLoader()));
    private final EvaluationContext context;
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    public SpelAuthorizationService() {
        try {
            SimpleEvaluationContext sandbox = SimpleEvaluationContext.forPropertyAccessors(new MapAccessor(false))
                    .withAssignmentDisabled()
                    .build();
            sandbox.setVariable("contains", SpelPolicyFunctions.class.getMethod("contains", Object.class, Object.class));
            sandbox.setVariable("like", SpelPolicyFunctions.class.getMethod("like", Object.class, String.class));
            this.context = sandbox;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Policy functions are missing", e);
        }
    }

    @Override
    public void registerPolicy(String action, String expression) {
        try {
            expressions.put(action, parser.parseRaw(SpelCompiler.compile(PolicyExpressionParser.parse(expression))));
        } catch (Exception e) {
            throw new SpelPolicyRegistrationException(
                    "Failed to register SpEL policy for action '" + action + "': " + expression, e);
        }
    }

    @Override
    public boolean isAuthorized(AuthorizationRequest request) {
        Expression expression = requireExpression(request.action());
        Map<String, Object> participantAttributes;
        Map<String, Object> namedArguments;
        try {
            participantAttributes = parseObject(request.principalAttributesJson());
            namedArguments = buildNamedArguments(request.argumentsJson(), request.parameterNames());
        } catch (Exception e) {
            throw new SpelAuthorizationException("Failed to parse request for action: " + request.action(), e);
        }
        return evaluate(expression, participantAttributes, namedArguments);
    }

    /**
     * Evaluates the policy registered for {@code action} against already-parsed participant
     * attributes and named method arguments (neither may be null).
     *
     * @return {@code true} if the request is permitted, {@code false} if denied
     * @throws SpelAuthorizationException if no policy is registered for the action
     */
    public boolean isAuthorized(String action, Map<String, Object> participantAttributes, Map<String, Object> namedArguments) {
        return evaluate(requireExpression(action), participantAttributes, namedArguments);
    }

    @Override
    public boolean hasPolicy(String action) {
        return expressions.containsKey(action);
    }

    @Override
    public void removePolicy(String action) {
        expressions.remove(action);
    }

    private Expression requireExpression(String action) {
        Expression expression = expressions.get(action);
        if (expression == null) {
            throw new SpelAuthorizationException("No policy registered for action: " + action);
        }
        return expression;
    }

    private boolean evaluate(Expression expression, Map<String, Object> participantAttributes, Map<String, Object> namedArguments) {
        Map<String, Object> root = Map.of("sub", participantAttributes, "obj", namedArguments);
        boolean ret;
        try {
            ret = Boolean.TRUE.equals(expression.getValue(context, root, Boolean.class));
        } catch (RuntimeException evaluationError) {
            // A missing attribute or type mismatch denies rather than failing the request.
            ret = false;
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(String json) {
        Map<String, Object> ret;
        if (json == null || json.isBlank()) {
            ret = Map.of();
        } else {
            ret = MAPPER.readValue(json, Map.class);
        }
        return ret;
    }

    /**
     * Maps the positional JSON argument array onto a named object using the parameter names, so a
     * policy path like {@code order.amount} resolves against the argument named {@code order}.
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
