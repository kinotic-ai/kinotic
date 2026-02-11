package org.kinotic.persistence.internal.api.services.impl.security.graphos;

import lombok.Getter;

import java.util.*;

/**
 * Manages shared policies for a given set of domain and field policies
 */
public class SharedPolicyManager {
    @Getter
    private final PolicyExpression entityExpression;
    private final Map<String, PolicyExpression> fieldExpressions = new HashMap<>();
    private final Map<String, List<List<String>>> fieldPolicies;
    private final Set<String> sharedPolicies = new HashSet<>();

    /**
     * Constructs a new SharedPolicyManager
     * @param entityPolicies the Entity policies
     * @param fieldPolicies the field policies
     */
    public SharedPolicyManager(List<List<String>> entityPolicies,
                               Map<String, List<List<String>>> fieldPolicies) {
        this.fieldPolicies = fieldPolicies;
        // Parse and store domain policies
        if (entityPolicies != null) {
            this.entityExpression = PolicyExpressionUtil.createPolicyExpression(entityPolicies);
            PolicyExpressionUtil.collectPolicies(this.entityExpression, sharedPolicies);
        } else {
            this.entityExpression = null;
        }

        // Parse and store field policies
        if (fieldPolicies != null) {
            for (Map.Entry<String, List<List<String>>> entry : fieldPolicies.entrySet()) {
                String fieldName = entry.getKey();
                PolicyExpression expression = PolicyExpressionUtil.createPolicyExpression(entry.getValue());
                this.fieldExpressions.put(fieldName, expression);
                PolicyExpressionUtil.collectPolicies(expression, sharedPolicies);
            }
        }
    }

    public Map<String, PolicyExpression> getFieldExpressions() {
        return Collections.unmodifiableMap(fieldExpressions);
    }

    public Map<String, List<List<String>>> getFieldPolicies() {
        return Collections.unmodifiableMap(fieldPolicies);
    }

    public Set<String> getSharedPolicies() {
        return Collections.unmodifiableSet(sharedPolicies);
    }

}

