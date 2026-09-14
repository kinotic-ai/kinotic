package org.kinotic.auth;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import org.kinotic.auth.api.engine.AuthorizationEngine;
import org.kinotic.auth.api.engine.AuthorizationRequest;
import org.kinotic.auth.compilers.CelCompiler;
import org.kinotic.auth.parsers.PolicyExpressionParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Benchmark-only {@link AuthorizationEngine} backed by CEL: each action's condition is compiled
 * once to a {@link CelRuntime.Program} and evaluated in-process against the request's participant
 * attributes ({@code r.sub}) and named arguments ({@code r.obj}).
 */
class CelEngine implements AuthorizationEngine {

    // CEL ints are 64-bit; parse JSON integers as Long so they adapt without a conversion step.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_LONG_FOR_INTS)
            .build();

    private final dev.cel.compiler.CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder()
            .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
            .addVar("r", MapType.create(SimpleType.STRING, SimpleType.DYN))
            .build();
    private final CelRuntime runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
    private final Map<String, CelRuntime.Program> programs = new ConcurrentHashMap<>();

    @Override
    public void registerPolicy(String action, String expression) {
        try {
            String cel = CelCompiler.compile(PolicyExpressionParser.parse(expression));
            CelAbstractSyntaxTree ast = compiler.compile(cel).getAst();
            programs.put(action, runtime.createProgram(ast));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to register CEL policy for action '" + action + "': " + expression, e);
        }
    }

    @Override
    public boolean isAuthorized(AuthorizationRequest request) {
        CelRuntime.Program program = programs.get(request.action());
        if (program == null) {
            throw new IllegalStateException("No policy registered for action: " + request.action());
        }
        Map<String, Object> env = Map.of("r", Map.of(
                "sub", parseObject(request.principalAttributesJson()),
                "obj", buildNamedArguments(request.argumentsJson(), request.parameterNames())));
        try {
            return Boolean.TRUE.equals(program.eval(env));
        } catch (Exception evaluationError) {
            // A missing attribute or type mismatch denies, matching the other engines.
            return false;
        }
    }

    @Override
    public boolean hasPolicy(String action) {
        return programs.containsKey(action);
    }

    @Override
    public void removePolicy(String action) {
        programs.remove(action);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(json, Map.class);
    }

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
