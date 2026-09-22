package org.kinotic.auth.engines;

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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Benchmark engine backed by CEL: each action's condition is compiled once to a
 * {@link CelRuntime.Program} and evaluated in-process against the request's participant
 * attributes ({@code r.sub}) and named arguments ({@code r.obj}).
 */
public final class CelEngine implements AuthorizationEngine, PreparsedEngine {

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
        return isAllowed(request.action(), RequestJson.subject(request), RequestJson.arguments(request));
    }

    @Override
    public boolean isAllowed(String action, Map<String, Object> subject, Map<String, Object> arguments) {
        CelRuntime.Program program = programs.get(action);
        if (program == null) {
            throw new IllegalStateException("No policy registered for action: " + action);
        }
        boolean ret;
        try {
            ret = Boolean.TRUE.equals(program.eval(Map.<String, Object>of("r", Map.<String, Object>of("sub", subject, "obj", arguments))));
        } catch (Exception evaluationError) {
            // A missing attribute or type mismatch denies, matching the other engines.
            ret = false;
        }
        return ret;
    }

    @Override
    public boolean hasPolicy(String action) {
        return programs.containsKey(action);
    }

    @Override
    public void removePolicy(String action) {
        programs.remove(action);
    }
}
