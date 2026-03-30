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
    void participantPathPreserved() {
        PolicyExpression expr = PolicyExpressionParser.parse("participant.department == entity.department");
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

        assertEquals("!(resource.deleted == true)", cedar);
    }

    @Test
    void containsExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse("participant.roles contains 'admin'");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("principal.roles.contains(\"admin\")", cedar);
    }

    @Test
    void inExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.status in ['active', 'pending']");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("[\"active\", \"pending\"].contains(resource.status)", cedar);
    }

    @Test
    void existsExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.approvedBy exists");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("resource has \"approvedBy\"", cedar);
    }

    @Test
    void existsNestedPath() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.address.city exists");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("resource.address has \"city\"", cedar);
    }

    @Test
    void existsParticipantPath() {
        PolicyExpression expr = PolicyExpressionParser.parse("participant.department exists");
        String cedar = CedarCompiler.compile(expr);

        assertEquals("principal has \"department\"", cedar);
    }

    @Test
    void complexRealWorldPolicy() {
        PolicyExpression expr = PolicyExpressionParser.parse(
                "participant.role contains 'finance' and order.amount < 50000");
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
