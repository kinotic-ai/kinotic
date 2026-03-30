package org.kinotic.auth.compilers;

import org.junit.jupiter.api.Test;
import org.kinotic.auth.api.expressions.PolicyExpression;
import org.kinotic.auth.parsers.PolicyExpressionParser;

import static org.junit.jupiter.api.Assertions.*;

class CedarCompilerTest {

    @Test
    void simpleEquality() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.department == 'finance'");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("resource.department == \"finance\"", cedar);
    }

    @Test
    void principalPathPreserved() {
        PolicyExpression expr = PolicyExpressionParser.parse("principal.department == entity.department");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("principal.department == resource.department", cedar);
    }

    @Test
    void contextPathPreserved() {
        PolicyExpression expr = PolicyExpressionParser.parse("context.time > 1000");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("context.time > 1000", cedar);
    }

    @Test
    void andExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse(
                "entity.status == 'active' and entity.amount < 50000");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("(resource.status == \"active\" && resource.amount < 50000)", cedar);
    }

    @Test
    void orExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse(
                "entity.status == 'active' or entity.status == 'pending'");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("(resource.status == \"active\" || resource.status == \"pending\")", cedar);
    }

    @Test
    void notExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse("not entity.deleted == true");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("!resource.deleted == true", cedar);
    }

    @Test
    void containsExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse("principal.roles contains 'admin'");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("principal.roles.contains(\"admin\")", cedar);
    }

    @Test
    void inExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.status in ['active', 'pending']");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("resource.status.containsAny([\"active\", \"pending\"])", cedar);
    }

    @Test
    void complexRealWorldPolicy() {
        PolicyExpression expr = PolicyExpressionParser.parse(
                "principal.role contains 'finance' and order.amount < 50000");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("(principal.role.contains(\"finance\") && resource.amount < 50000)", cedar);
    }

    @Test
    void numericComparisons() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.level >= 5");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("resource.level >= 5", cedar);
    }
}
