package org.kinotic.sql.domain.statements;

import org.kinotic.sql.domain.Statement;

/**
 * Represents a CREATE LIFECYCLE POLICY statement in the DSL.
 * Creates a reusable Elasticsearch ILM policy that data streams reference by name to age data out.
 * A null phase means that phase is omitted from the policy.
 * Created by Navíd Mitchell 🤝 Grok on 6/18/26.
 */
public record CreateLifecyclePolicyStatement(String policyName,
                                             RolloverConditions rollover,
                                             String deleteMinAge) implements Statement {
}
