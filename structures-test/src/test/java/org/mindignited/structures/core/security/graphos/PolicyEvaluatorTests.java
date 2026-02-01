package org.mindignited.structures.core.security.graphos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mindignited.structures.api.domain.SecurityContext;
import org.mindignited.structures.api.services.security.graphos.PolicyAuthorizationRequest;
import org.mindignited.structures.api.services.security.graphos.PolicyAuthorizer;
import org.mindignited.structures.internal.api.services.impl.security.graphos.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class PolicyEvaluatorTests {

    private static PolicyAuthorizer authorizer;
    private static SharedPolicyManager sharedPolicyManager;

    @BeforeAll
    public static void setUp() {
        // Shared domain and field policies
        List<List<String>> domainPolicies = List.of(
                List.of("policy3") // Domain policy
        );

        Map<String, List<List<String>>> fieldPolicies = Map.of(
                "firstName", List.of(
                        List.of("policy4", "policy5"), // AND group
                        List.of("policy6")             // OR group
                ),
                "lastName", List.of(
                        List.of("policy7")             // Single AND group
                )
        );

        // Initialize shared policy manager
        sharedPolicyManager = new SharedPolicyManager(domainPolicies, fieldPolicies);

        // Initialize mock authorizer
        authorizer = new MockPolicyAuthorizer();
    }

    @Test
    public void testPolicyEvaluatorWithFailedOperations() throws Exception {
        List<List<String>> operationPolicies = List.of(
                List.of("policy1", "policy2") // AND group
        );

        PolicyEvaluator evaluator = new PolicyEvaluatorWithOperation(authorizer, sharedPolicyManager, operationPolicies);

        AuthorizationResult result = evaluator.evaluatePolicies(null).get();

        assertFalse(result.operationAllowed()); // Operation policies are not satisfied
        assertTrue(result.entityAllowed());     // Domain policy is satisfied
        assertEquals(Map.of(
                "firstName", true, // Evaluated and satisfied
                "lastName", false // Evaluated and not satisfied
        ), result.fieldResults());
    }

    @Test
    public void testPolicyEvaluatorWithMissingDomainPolicy() throws Exception {
        List<List<String>> operationPolicies = List.of(
                List.of("policy1", "policy3") // AND group
        );

        // Modify sharedPolicyManager to exclude domain policy
        SharedPolicyManager modifiedPolicyManager = new SharedPolicyManager(null, sharedPolicyManager.getFieldPolicies());
        PolicyEvaluator evaluator = new PolicyEvaluatorWithOperation(authorizer, modifiedPolicyManager, operationPolicies);

        AuthorizationResult result = evaluator.evaluatePolicies(null).get();

        assertTrue(result.operationAllowed()); // Operation policies are satisfied
        assertTrue(result.entityAllowed());   // Domain policy is missing
        assertEquals(Map.of(
                "firstName", true, // Evaluated and satisfied
                "lastName", false // Evaluated and not satisfied
        ), result.fieldResults());
    }

    @Test
    public void testPolicyEvaluatorWithSuccessfulLastName() throws Exception {
        List<List<String>> operationPolicies = List.of(
                List.of("policy1", "policy3") // AND group
        );

        // Update MockPolicyAuthorizer to authorize policy7 for this test
        PolicyAuthorizer updatedAuthorizer = new PolicyAuthorizer() {
            @Override
            public CompletableFuture<Void> authorize(List<PolicyAuthorizationRequest> requests, SecurityContext securityContext) {
                for (PolicyAuthorizationRequest request : requests) {
                    if ("policy1".equals(request.policy()) ||
                            "policy3".equals(request.policy()) ||
                            "policy4".equals(request.policy()) ||
                            "policy6".equals(request.policy()) ||
                            "policy7".equals(request.policy())) { // Authorize policy7 here
                        request.authorize();
                    } else {
                        request.deny();
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        };

        PolicyEvaluator evaluator = new PolicyEvaluatorWithOperation(updatedAuthorizer, sharedPolicyManager, operationPolicies);

        AuthorizationResult result = evaluator.evaluatePolicies(null).get();

        assertTrue(result.operationAllowed()); // Operation policies are satisfied
        assertTrue(result.entityAllowed());    // Domain policy is satisfied
        assertEquals(Map.of(
                "firstName", true, // OR logic satisfied via policy6
                "lastName", true  // policy7 satisfied
        ), result.fieldResults());
    }

    @Test
    public void testPolicyEvaluatorWithSuccessfulOperations() throws Exception {
        List<List<String>> operationPolicies = List.of(
                List.of("policy1", "policy3") // AND group
        );

        PolicyEvaluator evaluator = new PolicyEvaluatorWithOperation(authorizer, sharedPolicyManager, operationPolicies);

        AuthorizationResult result = evaluator.evaluatePolicies(null).get();

        assertTrue(result.operationAllowed()); // Operation policies are satisfied
        assertTrue(result.entityAllowed());    // Domain policy is satisfied
        assertEquals(Map.of(
                "firstName", true, // Evaluated and satisfied
                "lastName", false // Evaluated and not satisfied
        ), result.fieldResults());
    }

    @Test
    public void testPolicyEvaluatorWithoutOperations() throws Exception {
        PolicyEvaluator evaluator = new PolicyEvaluatorWithoutOperation(authorizer, sharedPolicyManager);

        AuthorizationResult result = evaluator.evaluatePolicies(null).get();

        assertTrue(result.operationAllowed()); // No operation policies mean it's always allowed
        assertTrue(result.entityAllowed());    // Domain policy is satisfied
        assertEquals(Map.of(
                "firstName", true, // Evaluated and satisfied
                "lastName", false // Evaluated and not satisfied
        ), result.fieldResults());
    }

    @Test
    public void testSharedPolicyManagerInitialization() {
        assertNotNull(sharedPolicyManager.getEntityExpression());
        assertFalse(sharedPolicyManager.getFieldExpressions().isEmpty());
        assertTrue(sharedPolicyManager.getSharedPolicies().contains("policy3"));
        assertTrue(sharedPolicyManager.getSharedPolicies().contains("policy4"));
        assertTrue(sharedPolicyManager.getSharedPolicies().contains("policy5"));
        assertTrue(sharedPolicyManager.getSharedPolicies().contains("policy6"));
        assertTrue(sharedPolicyManager.getSharedPolicies().contains("policy7"));
    }

    // Mock Implementation for PolicyAuthorizer
    private static class MockPolicyAuthorizer implements PolicyAuthorizer {
        @Override
        public CompletableFuture<Void> authorize(List<PolicyAuthorizationRequest> requests, SecurityContext securityContext) {
            for (PolicyAuthorizationRequest request : requests) {
                // Authorize specific policies for testing
                if ("policy1".equals(request.policy()) ||
                        "policy3".equals(request.policy()) ||
                        "policy4".equals(request.policy()) ||
                        "policy6".equals(request.policy())) {
                    request.authorize();
                } else {
                    request.deny();
                }
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}



