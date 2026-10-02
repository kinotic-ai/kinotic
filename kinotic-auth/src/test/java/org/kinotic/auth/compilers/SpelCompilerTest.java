package org.kinotic.auth.compilers;

import org.junit.jupiter.api.Test;
import org.kinotic.auth.parsers.PolicyExpressionParser;
import org.kinotic.auth.parsers.PolicyParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the shape of the SpEL the compiler emits: only navigation, indexing, operators, literals and
 * the two registered functions, with every path operand null-guarded — the subset the sandboxed
 * evaluation context permits.
 */
class SpelCompilerTest {

    private static String compile(String policy) {
        return SpelCompiler.compile(PolicyExpressionParser.parse(policy));
    }

    @Test
    void containsUsesTheRegisteredFunction() {
        assertEquals("(sub.roles != null && #contains(sub.roles, 'finance'))",
                compile("participant.roles contains 'finance'"));
    }

    @Test
    void likeUsesTheRegisteredFunction() {
        assertEquals("(obj.user.email != null && #like(obj.user.email, '*@kinotic.ai'))",
                compile("user.email like '*@kinotic.ai'"));
    }

    @Test
    void inExpandsToAnEqualityChain() {
        assertEquals("(obj.doc.status != null && (obj.doc.status == 'active' || obj.doc.status == 'pending'))",
                compile("doc.status in ['active', 'pending']"));
    }

    @Test
    void existsIndexesTheLeaf() {
        assertEquals("obj.doc['approvedBy'] != null", compile("doc.approvedBy exists"));
    }

    @Test
    void comparisonsGuardEveryPathOperand() {
        assertEquals("(obj.transfer.amount != null && sub.transferLimit != null && obj.transfer.amount <= sub.transferLimit)",
                compile("transfer.amount <= participant.transferLimit"));
    }

    @Test
    void booleanOperatorsNest() {
        assertEquals("!(((sub.roles != null && #contains(sub.roles, 'admin')) || (obj.doc.status != null && obj.doc.status == 'x')))",
                compile("not (participant.roles contains 'admin' or doc.status == 'x')"));
    }

    @Test
    void stringLiteralsCannotBreakOutOfTheirQuotes() {
        String hostile = "x\\') || T(java.lang.Runtime).getRuntime().exec(\\'calc\\') || (\\'y";
        assertEquals("(sub.name != null && sub.name == 'x'') || T(java.lang.Runtime).getRuntime().exec(''calc'') || (''y')",
                compile("participant.name == '" + hostile + "'"));
    }

    @Test
    void contextAttributesAreRejected() {
        assertThrows(PolicyParseException.class, () -> compile("context.time == 'now'"));
    }
}
