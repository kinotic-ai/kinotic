package org.kinotic.persistence.internal.api.services.impl.security.graphos;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Utility class for working with {@link PolicyExpression}
 */
public class PolicyExpressionUtil {

    /**
     * Provides an easy way to get unique policies from an {@link PolicyExpression} by collecting them into a set
     *
     * @param expression the expression to collect policies from
     * @param policies the set to collect policies into
     */
    public static void collectPolicies(PolicyExpression expression, Set<String> policies) {
        if (expression instanceof LeafExpression) {
            policies.add(((LeafExpression) expression).policy());
        } else if (expression instanceof AndExpression) {
            for (PolicyExpression subExpression : ((AndExpression) expression).expressions()) {
                collectPolicies(subExpression, policies);
            }
        } else if (expression instanceof OrExpression) {
            for (PolicyExpression subExpression : ((OrExpression) expression).expressions()) {
                collectPolicies(subExpression, policies);
            }
        }
    }

    /**
     * Creates a {@link PolicyExpression} from a list of policies
     *
     * @param policies the policies to create the expression from
     * @return the created {@link PolicyExpression}
     */
    public static PolicyExpression createPolicyExpression(List<List<String>> policies) {
        if (policies == null || policies.isEmpty()) {
            throw new IllegalArgumentException("Policy structure cannot be null or empty");
        }

        List<PolicyExpression> orExpressions = new ArrayList<>();

        for (List<String> andGroup : policies) {
            if (andGroup == null || andGroup.isEmpty()) {
                throw new IllegalArgumentException("AND group cannot be null or empty");
            }

            List<PolicyExpression> andExpressions = new ArrayList<>();
            for (String policy : andGroup) {
                andExpressions.add(new LeafExpression(policy));
            }

            orExpressions.add(new AndExpression(andExpressions));
        }

        if (orExpressions.size() == 1) {
            return orExpressions.getFirst();
        }

        return new OrExpression(orExpressions);
    }

}
