package org.kinotic.auth.spel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Guards the sandbox: raw SpEL that would reach the JVM must be rejected in interpreted and
 * compiled mode alike, while the subset the compiler emits still evaluates.
 */
class SpelPolicySandboxTest {

    private static final List<SpelCompilerMode> MODES = List.of(SpelCompilerMode.OFF, SpelCompilerMode.IMMEDIATE);

    private static final List<String> ATTACKS = List.of(
            "T(java.lang.Runtime).getRuntime()",
            "T(java.lang.System).getProperty('user.dir')",
            "new java.lang.ProcessBuilder('id')",
            "@someBean",
            "sub.roles.clear()",
            "sub.roles.getClass()",
            "sub.roles[0].getClass().forName('java.lang.Runtime')",
            "#root.getClass().getClassLoader()",
            "''.getClass()",
            "sub.class",
            "obj.order.amount = 1",
            "obj.order['amount'] = 1",
            "obj.order.amount++",
            "sub.roles.?[#this.getClass() != null]",
            "sub.roles.![#this.toUpperCase()]",
            "#contains.invoke(null, sub.roles, 'finance')",
            "#contains.getClass()",
            "#like.getDeclaringClass()");

    private static final List<String> PERMITTED = List.of(
            "#contains(sub.roles, 'finance')",
            "#like(sub.email, '*@kinotic.ai')",
            "obj.order.amount < 50000L",
            "obj.order['amount'] != null",
            "!(obj.order.amount > 50000L) && (sub.roles != null || obj.order.amount == 1L)");

    private final EvaluationContext context = SpelPolicySandbox.newContext();
    private List<String> roles;
    private Map<String, Object> order;
    private Map<String, Object> root;

    @BeforeEach
    void setUp() {
        roles = new ArrayList<>(List.of("finance"));
        order = new HashMap<>();
        order.put("amount", 25000L);
        Map<String, Object> sub = new HashMap<>();
        sub.put("roles", roles);
        sub.put("email", "navid@kinotic.ai");
        Map<String, Object> obj = new HashMap<>();
        obj.put("order", order);
        root = new HashMap<>();
        root.put("sub", sub);
        root.put("obj", obj);
    }

    private static SpelExpressionParser parser(SpelCompilerMode mode) {
        return new SpelExpressionParser(new SpelParserConfiguration(mode, SpelPolicySandboxTest.class.getClassLoader()));
    }

    @Test
    void rejectsEverythingOutsideThePolicySubset() {
        for (SpelCompilerMode mode : MODES) {
            SpelExpressionParser parser = parser(mode);
            for (String attack : ATTACKS) {
                SpelExpression expression = parser.parseRaw(attack);
                // Repeated so that a compiled form, were one ever produced, is exercised too.
                for (int i = 0; i < 3; i++) {
                    assertThrows(SpelEvaluationException.class, () -> expression.getValue(context, root),
                            mode + " must reject: " + attack);
                }
            }
        }
        assertEquals(List.of("finance"), roles);
        assertEquals(25000L, order.get("amount"));
    }

    @Test
    void permitsThePolicySubset() {
        for (SpelCompilerMode mode : MODES) {
            SpelExpressionParser parser = parser(mode);
            for (String permitted : PERMITTED) {
                SpelExpression expression = parser.parseRaw(permitted);
                for (int i = 0; i < 3; i++) {
                    assertEquals(Boolean.TRUE, expression.getValue(context, root, Boolean.class),
                            mode + " must permit: " + permitted);
                }
            }
        }
    }
}
