package org.kinotic.idl.internal.directory;

import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.directory.SchemaService;
import org.kinotic.idl.api.directory.ServiceDeclaration;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ParameterDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.internal.support.TestService;
import org.kinotic.idl.internal.support.authz.TestEntityService;
import org.kinotic.idl.internal.support.authz.TestMisreferencingService;
import org.kinotic.idl.internal.support.authz.TestProjectService;
import org.kinotic.idl.internal.support.authz.TestUnderivableService;
import org.kinotic.idl.internal.support.authz.TestVmNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the authorization declarations a {@link SchemaService} derives while converting a resource service:
 * the resource decorator on the service, one check per function from its name, parameters and
 * {@code @AuthzCheck}, and the rejection of a function whose check does not resolve.
 */
@SpringBootTest
@ActiveProfiles("test")
public class SchemaServiceAuthzTest {

    @Autowired
    private SchemaService schemaService;

    private ServiceDefinition convert(Class<?> serviceInterface) {
        return schemaService.createForServices(List.of(new ServiceDeclaration(serviceInterface, serviceInterface)))
                            .getServices()
                            .iterator()
                            .next();
    }

    private static FunctionDefinition function(ServiceDefinition service, String name) {
        return service.getFunctions()
                      .stream()
                      .filter(f -> f.getName().equals(name))
                      .findFirst()
                      .orElseThrow();
    }

    private static AuthzCheckC3Decorator check(ServiceDefinition service, String functionName) {
        return function(service, functionName).findDecorator(AuthzCheckC3Decorator.class);
    }

    @Test
    public void resourceDecoratorCarriesTypeAndParent() {
        AuthzResourceC3Decorator resource = convert(TestProjectService.class).findDecorator(AuthzResourceC3Decorator.class);

        assertEquals("project", resource.getResourceType());
        assertEquals("application", resource.getParent());
        assertNull(convert(TestEntityService.class).findDecorator(AuthzResourceC3Decorator.class).getParent());
    }

    @Test
    public void viewVerbsCheckTheNamedResource() {
        AuthzCheckC3Decorator findById = check(convert(TestProjectService.class), "findById");

        assertEquals("project", findById.getResource());
        assertEquals("{id}", findById.getObjectId());
        assertEquals("project", findById.getPermissionResource());
        assertEquals("can_view", findById.getPermission());
    }

    @Test
    public void listingWithinTheParentChecksTheParent() {
        AuthzCheckC3Decorator findAll = check(convert(TestProjectService.class), "findAllForApplication");

        assertEquals("application", findAll.getResource());
        assertEquals("{applicationId}", findAll.getObjectId());
        assertEquals("project", findAll.getPermissionResource());
        assertEquals("can_view", findAll.getPermission());
    }

    @Test
    public void saveChecksTheArgumentCarryingAnId() {
        AuthzCheckC3Decorator save = check(convert(TestProjectService.class), "save");

        assertEquals("project", save.getResource());
        assertEquals("{value.id}", save.getObjectId());
        assertEquals("can_edit", save.getPermission());
    }

    @Test
    public void createChecksTheParentNamedByTheArgument() {
        AuthzCheckC3Decorator create = check(convert(TestProjectService.class), "createProjectIfNotExist");

        assertEquals("application", create.getResource());
        assertEquals("{project.applicationId}", create.getObjectId());
        assertEquals("project", create.getPermissionResource());
        assertEquals("can_edit", create.getPermission());
    }

    @Test
    public void declaredPermissionKeepsTheDerivedObject() {
        AuthzCheckC3Decorator retry = check(convert(TestProjectService.class), "retryRepoInitialization");

        assertEquals("project", retry.getResource());
        assertEquals("{projectId}", retry.getObjectId());
        assertEquals("can_edit", retry.getPermission());
        assertTrue(retry.getImplies().isEmpty());
    }

    @Test
    public void skippedParametersLeaveTheContractAndTheCheck() {
        FunctionDefinition deploy = function(convert(TestProjectService.class), "deploy");

        assertEquals(List.of("projectId"), deploy.getParameters().stream().map(ParameterDefinition::getName).toList());
        AuthzCheckC3Decorator check = deploy.findDecorator(AuthzCheckC3Decorator.class);
        assertEquals("{projectId}", check.getObjectId());
        assertEquals("can_deploy", check.getPermission());
        assertEquals(List.of("can_view"), check.getImplies());
    }

    @Test
    public void aFunctionNamingNothingChecksTheCallersScope() {
        AuthzCheckC3Decorator count = check(convert(TestProjectService.class), "count");

        assertEquals("application", count.getResource());
        assertEquals("{@applicationId}", count.getObjectId());
        assertEquals("project", count.getPermissionResource());
    }

    @Test
    public void templatedResourceIsKeptAsDeclared() {
        AuthzCheckC3Decorator findById = check(convert(TestEntityService.class), "findById");

        assertEquals("{entityDefinitionId}", findById.getResource());
        assertEquals("{id}", findById.getObjectId());
        assertEquals("{entityDefinitionId}", findById.getPermissionResource());
        assertEquals("can_view", findById.getPermission());
    }

    @Test
    public void aCheckOnThePlatformUsesItsFixedId() {
        ServiceDefinition service = convert(TestVmNodeService.class);

        AuthzCheckC3Decorator register = check(service, "register");
        assertEquals("platform", register.getResource());
        assertEquals("kinotic", register.getObjectId());
        assertEquals("vm_node", register.getPermissionResource());
        assertEquals("can_register_node", register.getPermission());

        AuthzCheckC3Decorator heartbeat = check(service, "heartbeat");
        assertEquals("vm_node", heartbeat.getResource());
        assertEquals("{registration.id}", heartbeat.getObjectId());

        AuthzCheckC3Decorator delete = check(service, "deleteByVmNodeId");
        assertEquals("{vmNodeId}", delete.getObjectId());
        assertEquals("can_delete", delete.getPermission());
    }

    @Test
    public void aFunctionDerivingNoPermissionRejectsTheService() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> convert(TestUnderivableService.class));

        assertTrue(e.getMessage().contains("frobnicate"));
    }

    @Test
    public void aTemplateNamingAnUnknownParameterRejectsTheService() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> convert(TestMisreferencingService.class));

        assertTrue(e.getMessage().contains("missing"));
    }

    @Test
    public void aServiceWithoutAResourceCarriesNoChecks() {
        ServiceDefinition service = convert(TestService.class);

        assertNull(service.findDecorator(AuthzResourceC3Decorator.class));
        for (FunctionDefinition function : service.getFunctions()) {
            assertNull(function.findDecorator(AuthzCheckC3Decorator.class), function.getName());
        }
    }

}
