package org.kinotic.auth.spel;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.MapAccessor;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * The evaluation context every compiled policy runs on. It permits read-only map navigation,
 * indexing, operators, literals and the {@code #contains}/{@code #like} functions of
 * {@link SpelPolicyFunctions}, and nothing else: no method invocation, type references,
 * constructors, bean references or assignment. A policy therefore cannot reach the JVM beyond the
 * request data it is handed, whatever its text contains.
 */
public final class SpelPolicySandbox {

    private SpelPolicySandbox() {}

    /**
     * Creates the sandboxed context. It is safe to share across concurrent evaluations and must be
     * treated as read-only once created.
     */
    public static EvaluationContext newContext() {
        SimpleEvaluationContext context = SimpleEvaluationContext.forPropertyAccessors(new MapAccessor(false))
                .withAssignmentDisabled()
                .build();
        try {
            context.setVariable("contains", SpelPolicyFunctions.class.getMethod("contains", Object.class, Object.class));
            context.setVariable("like", SpelPolicyFunctions.class.getMethod("like", Object.class, String.class));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Policy functions are missing", e);
        }
        return context;
    }
}
