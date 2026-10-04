package org.kinotic.idl.internal.directory;

import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.directory.SchemaService;
import org.kinotic.idl.api.directory.ServiceDeclaration;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ParameterDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzRoleDeclaration;
import org.kinotic.idl.internal.support.TestService;
import org.kinotic.idl.internal.support.authz.TestContradictoryService;
import org.kinotic.idl.internal.support.authz.TestEntityService;
import org.kinotic.idl.internal.support.authz.TestMemberService;
import org.kinotic.idl.internal.support.authz.TestMisnamedRoleService;
import org.kinotic.idl.internal.support.authz.TestMisreferencingService;
import org.kinotic.idl.internal.support.authz.TestProjectService;
import org.kinotic.idl.internal.support.authz.TestUnderivableService;
import org.kinotic.idl.internal.support.authz.TestVmNodeService;
import org.kinotic.idl.internal.support.authz.TestWorkloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the authorization declarations a {@link SchemaService} derives while converting a resource service:
 * the resource decorator on the service, one check per function from its name, parameters and
 * {@code @AuthzCheck}, the object a service names for functions naming none, the zone-only and consistent
 * declarations, and the rejection of a function whose check does not resolve.
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
    public void aServiceNamingItsObjectChecksEveryFunctionOnIt() {
        ServiceDefinition service = convert(TestMemberService.class);

        assertEquals("{@organizationId}", service.findDecorator(AuthzResourceC3Decorator.class).getObjectId());
        AuthzCheckC3Decorator findMembers = check(service, "findMembers");
        assertEquals("organization", findMembers.getResource());
        assertEquals("{@organizationId}", findMembers.getObjectId());
        assertEquals("organization", findMembers.getPermissionResource());
        assertEquals("can_view_members", findMembers.getPermission());
        assertFalse(findMembers.isConsistent());
        // an argument with an id, and a create, are checked on the service's object too
        assertEquals("{@organizationId}", check(service, "saveRole").getObjectId());
        assertEquals("organization", check(service, "saveRole").getResource());
        assertEquals("{@organizationId}", check(service, "createInvite").getObjectId());
        assertEquals("organization", check(service, "createInvite").getPermissionResource());

        // a function naming its own object is checked on it, not on the service's
        AuthzCheckC3Decorator findProject = check(service, "findProject");
        assertEquals("project", findProject.getResource());
        assertEquals("{projectId}", findProject.getObjectId());
        assertEquals("project", findProject.getPermissionResource());
    }

    @Test
    public void aConsistentCheckIsMarked() {
        AuthzCheckC3Decorator remove = check(convert(TestMemberService.class), "removeMember");

        assertEquals("can_manage_members", remove.getPermission());
        assertEquals("{@organizationId}", remove.getObjectId());
        assertTrue(remove.isConsistent());
        assertFalse(check(convert(TestProjectService.class), "save").isConsistent());
    }

    @Test
    public void aZoneOnlyFunctionCarriesNoCheck() {
        ServiceDefinition service = convert(TestMemberService.class);

        assertNull(check(service, "listAccessible"));
        assertEquals(List.of("type", "permission"),
                     function(service, "listAccessible").getParameters().stream().map(ParameterDefinition::getName).toList());
    }

    @Test
    public void zoneOnlyBesideACheckRejectsTheService() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> convert(TestContradictoryService.class));

        assertTrue(e.getMessage().contains("zone-only"));
    }

    @Test
    public void aFunctionDerivingNoPermissionRejectsTheService() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> convert(TestUnderivableService.class));

        assertTrue(e.getMessage().contains("frobnicate"));
    }

    @Test
    public void aServicesPermissionIsEveryFunctionsUnlessItDeclaresItsOwn() {
        ServiceDefinition service = convert(TestWorkloadService.class);

        assertEquals("can_manage_workloads", service.findDecorator(AuthzResourceC3Decorator.class).getPermission());
        // a verb deriving nothing, and one deriving a permission, both take the service's
        assertEquals("can_manage_workloads", check(service, "deployWorkload").getPermission());
        assertEquals("can_manage_workloads", check(service, "deleteWorkload").getPermission());
        assertEquals("platform:kinotic", check(service, "deployWorkload").getResource() + ":" + check(service, "deployWorkload").getObjectId());
        assertEquals("can_view_workloads", check(service, "findWorkload").getPermission());
    }

    @Test
    public void aDeclaredRoleIsCarriedByTheResource() {
        ServiceDefinition service = convert(TestVmNodeService.class);

        List<AuthzRoleDeclaration> roles = service.findDecorator(AuthzResourceC3Decorator.class).getRoles();
        assertEquals(1, roles.size());
        assertEquals("vm_node.registrar", roles.getFirst().getId());
        assertEquals(List.of("can_register_node"), roles.getFirst().getPermissions());
    }

    @Test
    public void aRoleNotNamedAfterItsTypeRejectsTheService() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> convert(TestMisnamedRoleService.class));

        assertTrue(e.getMessage().contains("gadget.keeper"), e.getMessage());
        assertTrue(e.getMessage().contains("widget."), e.getMessage());
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
