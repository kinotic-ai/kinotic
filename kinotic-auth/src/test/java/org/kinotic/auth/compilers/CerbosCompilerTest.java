package org.kinotic.auth.compilers;

import org.junit.jupiter.api.Test;
import org.kinotic.auth.api.expressions.PolicyExpression;
import org.kinotic.auth.parsers.PolicyExpressionParser;

import static org.junit.jupiter.api.Assertions.*;

class CerbosCompilerTest {

    @Test
    void simpleEquality() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.department == 'finance'");
        String cel = CerbosCompiler.compile(expr);

        assertEquals("R.attr.department == \"finance\"", cel);
    }

    @Test
    void participantPath() {
        PolicyExpression expr = PolicyExpressionParser.parse("participant.department == entity.department");
        String cel = CerbosCompiler.compile(expr);

        assertEquals("P.attr.department == R.attr.department", cel);
    }

    @Test
    void contextPath() {
        PolicyExpression expr = PolicyExpressionParser.parse("context.time > 1000");
        String cel = CerbosCompiler.compile(expr);

        assertEquals("request.auxData.jwt.time > 1000", cel);
    }

    @Test
    void andExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse(
                "entity.status == 'active' and entity.amount < 50000");
        String cel = CerbosCompiler.compile(expr);

        assertEquals("(R.attr.status == \"active\" && R.attr.amount < 50000)", cel);
    }

    @Test
    void orExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse(
                "entity.status == 'active' or entity.status == 'pending'");
        String cel = CerbosCompiler.compile(expr);

        assertEquals("(R.attr.status == \"active\" || R.attr.status == \"pending\")", cel);
    }

    @Test
    void notExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse("not entity.deleted == true");
        String cel = CerbosCompiler.compile(expr);

        assertEquals("!(R.attr.deleted == true)", cel);
    }

    @Test
    void containsExpression() {
        // "participant.roles contains 'admin'" means 'admin' is in the roles list
        // In CEL: "admin" in P.attr.roles
        PolicyExpression expr = PolicyExpressionParser.parse("participant.roles contains 'admin'");
        String cel = CerbosCompiler.compile(expr);

        assertEquals("\"admin\" in P.attr.roles", cel);
    }

    @Test
    void inExpression() {
        // "entity.status in ['active', 'pending']" means status is one of the values
        // In CEL: R.attr.status in ["active", "pending"]
        PolicyExpression expr = PolicyExpressionParser.parse("entity.status in ['active', 'pending']");
        String cel = CerbosCompiler.compile(expr);

        assertEquals("R.attr.status in [\"active\", \"pending\"]", cel);
    }

    @Test
    void existsExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.approvedBy exists");
        String cel = CerbosCompiler.compile(expr);

        assertEquals("has(R.attr.approvedBy)", cel);
    }

    @Test
    void existsNestedPath() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.address.city exists");
        String cel = CerbosCompiler.compile(expr);

        assertEquals("has(R.attr.address.city)", cel);
    }

    @Test
    void likeExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.email like '*@kinotic.ai'");
        String cel = CerbosCompiler.compile(expr);

        assertEquals("R.attr.email.matches(\"*@kinotic.ai\")", cel);
    }

    @Test
    void complexRealWorldPolicy() {
        PolicyExpression expr = PolicyExpressionParser.parse(
                "participant.role contains 'finance' and order.amount < 50000");
        String cel = CerbosCompiler.compile(expr);

        assertEquals("(\"finance\" in P.attr.role && R.attr.amount < 50000)", cel);
    }

    @Test
    void participantPathNoTrailingDot() {
        PolicyExpression expr = PolicyExpressionParser.parse("participant.id == 'alice'");
        String cel = CerbosCompiler.compile(expr);

        assertFalse(cel.contains("P.attr.id."), "should not produce double-dot path");
        assertEquals("P.attr.id == \"alice\"", cel);
    }

    @Test
    void numericComparisons() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.level >= 5");
        String cel = CerbosCompiler.compile(expr);

        assertEquals("R.attr.level >= 5", cel);
    }

    @Test
    void bareRootThrows() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.field == 'value'");
        // Should work fine with a field
        assertDoesNotThrow(() -> CerbosCompiler.compile(expr));
    }
}
