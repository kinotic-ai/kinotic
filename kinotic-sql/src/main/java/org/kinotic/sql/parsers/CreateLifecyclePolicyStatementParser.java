package org.kinotic.sql.parsers;

import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.CreateLifecyclePolicyStatement;
import org.kinotic.sql.domain.statements.RolloverConditions;
import org.kinotic.sql.parser.KinoticSQLParser;
import org.springframework.stereotype.Component;

/**
 * Parses CREATE LIFECYCLE POLICY statements into CreateLifecyclePolicyStatement objects.
 * Handles ILM policy creation with an optional hot-phase ROLLOVER and a DELETE phase.
 * Created by Navíd Mitchell 🤝 Grok on 6/18/26.
 */
@Component
public class CreateLifecyclePolicyStatementParser implements StatementParser {
    @Override
    public boolean supports(KinoticSQLParser.StatementContext ctx) {
        return ctx.createLifecyclePolicyStatement() != null;
    }

    @Override
    public Statement parse(KinoticSQLParser.StatementContext ctx) {
        KinoticSQLParser.CreateLifecyclePolicyStatementContext policyCtx = ctx.createLifecyclePolicyStatement();
        String policyName = policyCtx.ID().getText();

        RolloverConditions rollover = null;
        String deleteMinAge = null;

        for (KinoticSQLParser.LifecyclePhaseContext phase : policyCtx.lifecyclePhase()) {
            if (phase.ROLLOVER() != null) {
                rollover = parseRollover(phase);
            } else if (phase.DELETE() != null) {
                deleteMinAge = stripQuotes(phase.STRING().getText());
            }
        }

        return new CreateLifecyclePolicyStatement(policyName, rollover, deleteMinAge);
    }

    private RolloverConditions parseRollover(KinoticSQLParser.LifecyclePhaseContext phase) {
        String maxAge = null;
        String maxSize = null;
        Integer maxDocs = null;
        for (KinoticSQLParser.RolloverConditionContext condition : phase.rolloverCondition()) {
            if (condition.MAX_AGE() != null) {
                maxAge = stripQuotes(condition.STRING().getText());
            } else if (condition.MAX_SIZE() != null) {
                maxSize = stripQuotes(condition.STRING().getText());
            } else if (condition.MAX_DOCS() != null) {
                maxDocs = Integer.valueOf(condition.INTEGER_LITERAL().getText());
            }
        }
        return new RolloverConditions(maxAge, maxSize, maxDocs);
    }

    private static String stripQuotes(String value) {
        return value.replaceAll("'", "");
    }
}
