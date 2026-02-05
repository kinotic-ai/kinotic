package org.mindignited.structures.tests.core.security.graphos;

import org.junit.jupiter.api.Test;
import org.mindignited.continuum.api.exceptions.AuthorizationException;
import org.mindignited.continuum.idl.api.schema.ObjectC3Type;
import org.mindignited.structures.api.domain.SecurityContext;
import org.mindignited.structures.api.domain.Structure;
import org.mindignited.structures.api.domain.idl.decorators.EntityServiceDecoratorsConfig;
import org.mindignited.structures.api.domain.idl.decorators.EntityServiceDecoratorsDecorator;
import org.mindignited.structures.api.domain.idl.decorators.PolicyDecorator;
import org.mindignited.structures.api.services.security.graphos.PolicyAuthorizationRequest;
import org.mindignited.structures.api.services.security.graphos.StructurePolicyAuthorizationService;
import org.mindignited.structures.api.services.security.graphos.PolicyAuthorizer;
import org.mindignited.structures.api.domain.EntityOperation;
import org.mindignited.structures.api.domain.DecoratedProperty;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

public class PolicyAuthorizationServiceTest {

    private final PolicyAuthorizer authorizer = new MockPolicyAuthorizer();

    @Test
    public void testAuthorizeWithNoPoliciesOnStructure() {
        Structure structure = new Structure();
        structure.setApplicationId("testApplication");
        structure.setName("testName");

        ObjectC3Type entityDefinition = new ObjectC3Type();
        structure.setEntityDefinition(entityDefinition);

        StructurePolicyAuthorizationService service = new StructurePolicyAuthorizationService(structure, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.FIND_ALL, null);

        assertDoesNotThrow(result::join); // Should pass since there are no policies
    }

    @Test
    public void testAuthorizeWithEntityOnlyPolicies(){
        Structure structure = new Structure();
        structure.setApplicationId("testApplication");
        structure.setName("testName");

        ObjectC3Type entityDefinition = new ObjectC3Type();
        entityDefinition.addDecorator(new PolicyDecorator().setPolicies(List.of(List.of("policy1"))));
        structure.setEntityDefinition(entityDefinition);

        StructurePolicyAuthorizationService service = new StructurePolicyAuthorizationService(structure, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.FIND_ALL, null);

        assertDoesNotThrow(result::join); // Should pass since the READ operation policy is allowed
    }

    @Test
    public void testAuthorizeDeniedWithEntityOnlyPolicies(){
        Structure structure = new Structure();
        structure.setApplicationId("testApplication");
        structure.setName("testName");

        ObjectC3Type entityDefinition = new ObjectC3Type();
        entityDefinition.addDecorator(new PolicyDecorator().setPolicies(List.of(List.of("policy2"))));
        structure.setEntityDefinition(entityDefinition);

        StructurePolicyAuthorizationService service = new StructurePolicyAuthorizationService(structure, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.FIND_ALL, null);

        Throwable exception = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(AuthorizationException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().endsWith("Entity access not allowed.")); // Fails due to policy2
    }

    @Test
    public void testAuthorizeReadOperationWithNoFieldPolicies() {
        Structure structure = createStructureWithNoFieldPolicies();
        StructurePolicyAuthorizationService service = new StructurePolicyAuthorizationService(structure, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.FIND_ALL, null);

        assertDoesNotThrow(result::join); // Should pass since the READ operation policy is allowed
    }

    @Test
    public void testAuthorizeWriteOperationFails() {
        Structure structure = createStructureWithNoFieldPolicies();
        StructurePolicyAuthorizationService service = new StructurePolicyAuthorizationService(structure, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.SAVE, null);

        Throwable exception = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(AuthorizationException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Operation SAVE not allowed.")); // Fails due to policy2
    }

    @Test
    public void testAuthorizeReadFailsDueToFieldPolicy() {
        Structure structure = createStructureWithFieldPolicies();
        StructurePolicyAuthorizationService service = new StructurePolicyAuthorizationService(structure, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.FIND_ALL, null);

        Throwable exception = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(AuthorizationException.class, exception.getCause());
        assertEquals("Structure testapplication.testname Fields [lastName] access not allowed.", exception.getCause().getMessage());
    }

    @Test
    public void testAuthorizeWriteFailsDueToOperationPolicy() {
        Structure structure = createStructureWithFieldPolicies();
        StructurePolicyAuthorizationService service = new StructurePolicyAuthorizationService(structure, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.SAVE, null);

        Throwable exception = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(AuthorizationException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Operation SAVE not allowed."));
    }

    private Structure createStructureWithNoFieldPolicies() {
        Structure structure = new Structure();
        structure.setApplicationId("testApplication");
        structure.setName("testName");

        ObjectC3Type entityDefinition = new ObjectC3Type();
        structure.setEntityDefinition(entityDefinition);

        // No field policies
        structure.setDecoratedProperties(List.of());

        // Add operation-level decorators
        PolicyDecorator entityPolicyDecoratorForRead = new PolicyDecorator();
        entityPolicyDecoratorForRead.setPolicies(List.of(
                List.of("policy1") // Policy allowed by MockPolicyAuthorizer
        ));

        PolicyDecorator entityPolicyDecoratorForSave = new PolicyDecorator();
        entityPolicyDecoratorForSave.setPolicies(List.of(
                List.of("policy2") // Policy denied by MockPolicyAuthorizer
        ));

        EntityServiceDecoratorsDecorator operationDecorator = new EntityServiceDecoratorsDecorator();
        EntityServiceDecoratorsConfig config = new EntityServiceDecoratorsConfig();
        config.setSave(List.of(entityPolicyDecoratorForSave)); // Restricted SAVE operation
        config.setFindAll(List.of(entityPolicyDecoratorForRead)); // Allowed FindAll operation
        operationDecorator.setConfig(config);
        entityDefinition.addDecorator(operationDecorator);

        return structure;
    }

    private Structure createStructureWithFieldPolicies() {
        Structure structure = new Structure();
        structure.setApplicationId("testApplication");
        structure.setName("testName");


        ObjectC3Type entityDefinition = new ObjectC3Type();
        structure.setEntityDefinition(entityDefinition);

        // Add field-level policies
        DecoratedProperty firstNameProperty = new DecoratedProperty();
        firstNameProperty.setJsonPath("firstName");
        PolicyDecorator firstNamePolicy = new PolicyDecorator();
        firstNamePolicy.setPolicies(List.of(
                List.of("policy4", "policy5"), // AND group
                List.of("policy6")             // OR group
        ));
        firstNameProperty.setDecorators(List.of(firstNamePolicy));

        DecoratedProperty lastNameProperty = new DecoratedProperty();
        lastNameProperty.setJsonPath("lastName");
        PolicyDecorator lastNamePolicy = new PolicyDecorator();
        lastNamePolicy.setPolicies(List.of(
                List.of("policy7") // Single policy
        ));
        lastNameProperty.setDecorators(List.of(lastNamePolicy));

        structure.setDecoratedProperties(List.of(firstNameProperty, lastNameProperty));

        // Add operation-level decorators
        PolicyDecorator entityPolicyDecoratorForRead = new PolicyDecorator();
        entityPolicyDecoratorForRead.setPolicies(List.of(
                List.of("policy1") // Policy allowed by MockPolicyAuthorizer
        ));

        PolicyDecorator entityPolicyDecoratorForSave = new PolicyDecorator();
        entityPolicyDecoratorForSave.setPolicies(List.of(
                List.of("policy2") // Policy denied by MockPolicyAuthorizer
        ));

        EntityServiceDecoratorsDecorator operationDecorator = new EntityServiceDecoratorsDecorator();
        EntityServiceDecoratorsConfig config = new EntityServiceDecoratorsConfig();
        config.setSave(List.of(entityPolicyDecoratorForSave)); // Restricted SAVE operation
        config.setFindAll(List.of(entityPolicyDecoratorForRead)); // Allowed FindAll operation
        operationDecorator.setConfig(config);
        entityDefinition.addDecorator(operationDecorator);

        return structure;
    }

    private static class MockPolicyAuthorizer implements PolicyAuthorizer {
        @Override
        public CompletableFuture<Void> authorize(List<PolicyAuthorizationRequest> requests, SecurityContext securityContext) {
            for (PolicyAuthorizationRequest request : requests) {
                switch (request.policy()) {
                    case "policy1", "policy4", "policy5", "policy6" -> request.authorize(); // Authorized policies
                    case "policy2", "policy7" -> request.deny(); // Denied policies
                    default -> request.deny(); // Default deny
                }
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}






