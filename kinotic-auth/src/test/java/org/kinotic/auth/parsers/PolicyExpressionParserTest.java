package org.kinotic.auth.parsers;

import org.junit.jupiter.api.Test;
import org.kinotic.auth.api.expressions.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PolicyExpressionParserTest {

    @Test
    void simpleEquality() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.department == 'finance'");

        assertInstanceOf(ComparisonExpression.class, expr);
        ComparisonExpression comp = (ComparisonExpression) expr;
        assertEquals(ComparisonOperator.EQUALS, comp.operator());
        assertEquals("entity", comp.left().root());
        assertEquals(List.of("department"), comp.left().fields());

        assertInstanceOf(LiteralValue.class, comp.right());
        assertEquals("finance", ((LiteralValue) comp.right()).asString());
    }

    @Test
    void pathToPathComparison() {
        PolicyExpression expr = PolicyExpressionParser.parse("principal.department == entity.department");

        assertInstanceOf(ComparisonExpression.class, expr);
        ComparisonExpression comp = (ComparisonExpression) expr;
        assertEquals(ComparisonOperator.EQUALS, comp.operator());
        assertEquals("principal", comp.left().root());
        assertEquals("entity", ((AttributePath) comp.right()).root());
    }

    @Test
    void andExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse(
                "principal.department == entity.department and entity.status == 'active'");

        assertInstanceOf(AndExpression.class, expr);
        AndExpression and = (AndExpression) expr;
        assertInstanceOf(ComparisonExpression.class, and.left());
        assertInstanceOf(ComparisonExpression.class, and.right());
    }

    @Test
    void orExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse(
                "entity.status == 'active' or entity.status == 'pending'");

        assertInstanceOf(OrExpression.class, expr);
    }

    @Test
    void andOrPrecedence() {
        // AND binds tighter than OR
        PolicyExpression expr = PolicyExpressionParser.parse(
                "entity.a == 1 or entity.b == 2 and entity.c == 3");

        // Should parse as: a==1 OR (b==2 AND c==3)
        assertInstanceOf(OrExpression.class, expr);
        OrExpression or = (OrExpression) expr;
        assertInstanceOf(ComparisonExpression.class, or.left());
        assertInstanceOf(AndExpression.class, or.right());
    }

    @Test
    void notExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse("not entity.deleted == true");

        assertInstanceOf(NotExpression.class, expr);
        NotExpression not = (NotExpression) expr;
        assertInstanceOf(ComparisonExpression.class, not.expression());
    }

    @Test
    void parenthesizedExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse(
                "(entity.a == 1 or entity.b == 2) and entity.c == 3");

        // Parens override precedence: (a==1 OR b==2) AND c==3
        assertInstanceOf(AndExpression.class, expr);
        AndExpression and = (AndExpression) expr;
        assertInstanceOf(OrExpression.class, and.left());
        assertInstanceOf(ComparisonExpression.class, and.right());
    }

    @Test
    void inExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse(
                "entity.status in ['active', 'pending', 'review']");

        assertInstanceOf(ComparisonExpression.class, expr);
        ComparisonExpression comp = (ComparisonExpression) expr;
        assertEquals(ComparisonOperator.IN, comp.operator());

        assertInstanceOf(ArrayValue.class, comp.right());
        ArrayValue arr = (ArrayValue) comp.right();
        assertEquals(3, arr.values().size());
        assertEquals("active", arr.values().get(0).asString());
    }

    @Test
    void containsExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse("principal.roles contains 'admin'");

        assertInstanceOf(ComparisonExpression.class, expr);
        ComparisonExpression comp = (ComparisonExpression) expr;
        assertEquals(ComparisonOperator.CONTAINS, comp.operator());
        assertEquals("admin", ((LiteralValue) comp.right()).asString());
    }

    @Test
    void existsExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.approvedBy exists");

        assertInstanceOf(ComparisonExpression.class, expr);
        ComparisonExpression comp = (ComparisonExpression) expr;
        assertEquals(ComparisonOperator.EXISTS, comp.operator());
        assertNull(comp.right());
    }

    @Test
    void likeExpression() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.email like '*@kinotic.ai'");

        assertInstanceOf(ComparisonExpression.class, expr);
        ComparisonExpression comp = (ComparisonExpression) expr;
        assertEquals(ComparisonOperator.LIKE, comp.operator());
        assertEquals("*@kinotic.ai", ((LiteralValue) comp.right()).asString());
    }

    @Test
    void numericComparisons() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.amount < 50000");

        assertInstanceOf(ComparisonExpression.class, expr);
        ComparisonExpression comp = (ComparisonExpression) expr;
        assertEquals(ComparisonOperator.LESS_THAN, comp.operator());
        assertEquals(50000L, ((LiteralValue) comp.right()).asLong());
    }

    @Test
    void decimalLiteral() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.rate >= 3.14");

        assertInstanceOf(ComparisonExpression.class, expr);
        ComparisonExpression comp = (ComparisonExpression) expr;
        assertEquals(ComparisonOperator.GREATER_THAN_OR_EQUAL, comp.operator());
        assertEquals(3.14, ((LiteralValue) comp.right()).asDouble());
    }

    @Test
    void booleanLiteral() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.active == true");

        assertInstanceOf(ComparisonExpression.class, expr);
        ComparisonExpression comp = (ComparisonExpression) expr;
        assertEquals(true, ((LiteralValue) comp.right()).asBoolean());
    }

    @Test
    void caseInsensitiveKeywords() {
        PolicyExpression expr = PolicyExpressionParser.parse(
                "entity.a == 1 AND entity.b == 2 OR entity.c == 3");

        assertInstanceOf(OrExpression.class, expr);
    }

    @Test
    void complexRealWorldPolicy() {
        PolicyExpression expr = PolicyExpressionParser.parse(
                "principal.role contains 'finance' and order.amount < 50000 and order.department == principal.department");

        assertInstanceOf(AndExpression.class, expr);
    }

    @Test
    void deepNestedPath() {
        PolicyExpression expr = PolicyExpressionParser.parse("entity.address.city.name == 'Austin'");

        assertInstanceOf(ComparisonExpression.class, expr);
        ComparisonExpression comp = (ComparisonExpression) expr;
        assertEquals("entity", comp.left().root());
        assertEquals(List.of("address", "city", "name"), comp.left().fields());
        assertEquals("address.city.name", comp.left().fieldPath());
    }

    @Test
    void blankExpressionThrows() {
        assertThrows(PolicyParseException.class, () -> PolicyExpressionParser.parse(""));
        assertThrows(PolicyParseException.class, () -> PolicyExpressionParser.parse("   "));
        assertThrows(PolicyParseException.class, () -> PolicyExpressionParser.parse(null));
    }

    @Test
    void invalidSyntaxThrows() {
        assertThrows(PolicyParseException.class, () -> PolicyExpressionParser.parse("entity.a =="));
        assertThrows(PolicyParseException.class, () -> PolicyExpressionParser.parse("== 'value'"));
        assertThrows(PolicyParseException.class, () -> PolicyExpressionParser.parse("entity.a ** 5"));
    }

    @Test
    void unterminatedStringThrows() {
        assertThrows(PolicyParseException.class, () -> PolicyExpressionParser.parse("entity.a == 'unterminated"));
    }
}
