package org.kinotic.test.tests.core.security.graphos;

import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.SecurityContext;
import org.kinotic.persistence.api.model.idl.decorators.EntityServiceDecoratorsConfig;
import org.kinotic.persistence.api.model.idl.decorators.EntityServiceDecoratorsDecorator;
import org.kinotic.persistence.api.model.idl.decorators.PolicyDecorator;
import org.kinotic.persistence.api.services.security.graphos.PolicyAuthorizationRequest;
import org.kinotic.persistence.api.services.security.graphos.EntityDefinitionPolicyAuthorizationService;
import org.kinotic.persistence.api.services.security.graphos.PolicyAuthorizer;
import org.kinotic.persistence.api.model.EntityOperation;
import org.kinotic.persistence.api.model.DecoratedProperty;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

public class PolicyAuthorizationServiceTest {

    private final PolicyAuthorizer authorizer = new MockPolicyAuthorizer();

    @Test
    public void testAuthorizeWithNoPoliciesOnStructure() {
        EntityDefinition structure = new EntityDefinition();
        structure.setApplicationId("testApplication");
        structure.setName("testName");

        ObjectC3Type entityDefinition = new ObjectC3Type();
        structure.setSchema(entityDefinition);

        EntityDefinitionPolicyAuthorizationService service = new EntityDefinitionPolicyAuthorizationService(structure, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.FIND_ALL, null);

        assertDoesNotThrow(result::join); // Should pass since there are no policies
    }

    @Test
    public void testAuthorizeWithEntityOnlyPolicies(){
        EntityDefinition structure = new EntityDefinition();
        structure.setApplicationId("testApplication");
        structure.setName("testName");

        ObjectC3Type entityDefinition = new ObjectC3Type();
        entityDefinition.addDecorator(new PolicyDecorator().setPolicies(List.of(List.of("policy1"))));
        structure.setSchema(entityDefinition);

        EntityDefinitionPolicyAuthorizationService service = new EntityDefinitionPolicyAuthorizationService(structure, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.FIND_ALL, null);

        assertDoesNotThrow(result::join); // Should pass since the READ operation policy is allowed
    }

    @Test
    public void testAuthorizeDeniedWithEntityOnlyPolicies(){
        EntityDefinition structure = new EntityDefinition();
        structure.setApplicationId("testApplication");
        structure.setName("testName");

        ObjectC3Type entityDefinition = new ObjectC3Type();
        entityDefinition.addDecorator(new PolicyDecorator().setPolicies(List.of(List.of("policy2"))));
        structure.setSchema(entityDefinition);

        EntityDefinitionPolicyAuthorizationService service = new EntityDefinitionPolicyAuthorizationService(structure, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.FIND_ALL, null);

        Throwable exception = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(AuthorizationException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().endsWith("Entity access not allowed.")); // Fails due to policy2
    }

    @Test
    public void testAuthorizeReadOperationWithNoFieldPolicies() {
        EntityDefinition entityDefinition = createStructureWithNoFieldPolicies();
        EntityDefinitionPolicyAuthorizationService service = new EntityDefinitionPolicyAuthorizationService(entityDefinition, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.FIND_ALL, null);

        assertDoesNotThrow(result::join); // Should pass since the READ operation policy is allowed
    }

    @Test
    public void testAuthorizeWriteOperationFails() {
        EntityDefinition entityDefinition = createStructureWithNoFieldPolicies();
        EntityDefinitionPolicyAuthorizationService service = new EntityDefinitionPolicyAuthorizationService(entityDefinition, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.SAVE, null);

        Throwable exception = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(AuthorizationException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Operation SAVE not allowed.")); // Fails due to policy2
    }

    @Test
    public void testAuthorizeReadFailsDueToFieldPolicy() {
        EntityDefinition entityDefinition = createStructureWithFieldPolicies();
        EntityDefinitionPolicyAuthorizationService service = new EntityDefinitionPolicyAuthorizationService(entityDefinition, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.FIND_ALL, null);

        Throwable exception = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(AuthorizationException.class, exception.getCause());
        assertEquals("testapplication.testname Fields [lastName] access not allowed.", exception.getCause().getMessage());
    }

    @Test
    public void testAuthorizeWriteFailsDueToOperationPolicy() {
        EntityDefinition entityDefinition = createStructureWithFieldPolicies();
        EntityDefinitionPolicyAuthorizationService service = new EntityDefinitionPolicyAuthorizationService(entityDefinition, authorizer);

        CompletableFuture<Void> result = service.authorize(EntityOperation.SAVE, null);

        Throwable exception = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(AuthorizationException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Operation SAVE not allowed."));
    }

    private EntityDefinition createStructureWithNoFieldPolicies() {
        EntityDefinition structure = new EntityDefinition();
        structure.setApplicationId("testApplication");
        structure.setName("testName");

        ObjectC3Type entityDefinition = new ObjectC3Type();
        structure.setSchema(entityDefinition);

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

    private EntityDefinition createStructureWithFieldPolicies() {
        EntityDefinition structure = new EntityDefinition();
        structure.setApplicationId("testApplication");
        structure.setName("testName");


        ObjectC3Type entityDefinition = new ObjectC3Type();
        structure.setSchema(entityDefinition);

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






