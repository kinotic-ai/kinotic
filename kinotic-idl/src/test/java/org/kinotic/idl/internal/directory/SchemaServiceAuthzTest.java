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
import org.kinotic.idl.internal.support.authz.TestResourcelessCheckService;
import org.kinotic.idl.internal.support.authz.TestRowsService;
import org.kinotic.idl.internal.support.authz.TestUncheckedResourceService;
import org.kinotic.idl.internal.support.authz.TestUncheckedService;
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
 * {@code @AuthzCheck}, the object a service names for functions naming none, the unchecked mark on a function
 * and on a whole service declaring no resource, the consistent declaration, and the rejections: a function
 * whose check does not resolve, a type named by a template, which only a resource id may be, a service marked
 * unchecked beside its resource, and a check declared on a service with no resource.
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
        assertNull(convert(TestMemberService.class).findDecorator(AuthzResourceC3Decorator.class).getParent());
    }

    @Test
    public void viewVerbsCheckTheNamedResource() {
        AuthzCheckC3Decorator findById = check(convert(TestProjectService.class), "findById");

        assertEquals("project", findById.getResource());
        assertEquals("{id}", findById.getResourceId());
        assertEquals("project", findById.getPermissionResource());
        assertEquals("can_view", findById.getPermission());
    }

    @Test
    public void listingWithinTheParentChecksTheParent() {
        AuthzCheckC3Decorator findAll = check(convert(TestProjectService.class), "findAllForApplication");

        assertEquals("application", findAll.getResource());
        assertEquals("{applicationId}", findAll.getResourceId());
        assertEquals("project", findAll.getPermissionResource());
        assertEquals("can_view", findAll.getPermission());
    }

    @Test
    public void saveChecksTheArgumentCarryingAnId() {
        AuthzCheckC3Decorator save = check(convert(TestProjectService.class), "save");

        assertEquals("project", save.getResource());
        assertEquals("{value.id}", save.getResourceId());
        assertEquals("can_edit", save.getPermission());
    }

    @Test
    public void createChecksTheParentNamedByTheArgument() {
        AuthzCheckC3Decorator create = check(convert(TestProjectService.class), "createProjectIfNotExist");

        assertEquals("application", create.getResource());
        assertEquals("{project.applicationId}", create.getResourceId());
        assertEquals("project", create.getPermissionResource());
        assertEquals("can_edit", create.getPermission());
    }

    @Test
    public void declaredPermissionKeepsTheDerivedObject() {
        AuthzCheckC3Decorator retry = check(convert(TestProjectService.class), "retryRepoInitialization");

        assertEquals("project", retry.getResource());
        assertEquals("{projectId}", retry.getResourceId());
        assertEquals("can_edit", retry.getPermission());
        assertTrue(retry.getImplies().isEmpty());
    }

    @Test
    public void skippedParametersLeaveTheContractAndTheCheck() {
        FunctionDefinition deploy = function(convert(TestProjectService.class), "deploy");

        assertEquals(List.of("projectId"), deploy.getParameters().stream().map(ParameterDefinition::getName).toList());
        AuthzCheckC3Decorator check = deploy.findDecorator(AuthzCheckC3Decorator.class);
        assertEquals("{projectId}", check.getResourceId());
        assertEquals("can_deploy", check.getPermission());
        assertEquals(List.of("can_view"), check.getImplies());
    }

    @Test
    public void aFunctionNamingNothingChecksTheCallersScope() {
        AuthzCheckC3Decorator count = check(convert(TestProjectService.class), "count");

        assertEquals("application", count.getResource());
        assertEquals("{@applicationId}", count.getResourceId());
        assertEquals("project", count.getPermissionResource());
    }

    @Test
    public void aTemplateOnTheCheckedResourcesTypeRejectsTheService() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> convert(TestEntityService.class));

        assertTrue(e.getMessage().contains("{entityDefinitionId}"), e.getMessage());
        assertTrue(e.getMessage().contains("findById"), e.getMessage());
    }

    @Test
    public void aTemplateOnTheServicesTypeRejectsTheService() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> convert(TestRowsService.class));

        assertTrue(e.getMessage().contains("{entityDefinitionId}"), e.getMessage());
        assertTrue(e.getMessage().contains("TestRowsService"), e.getMessage());
    }

    @Test
    public void aCheckOnThePlatformUsesItsFixedId() {
        ServiceDefinition service = convert(TestVmNodeService.class);

        AuthzCheckC3Decorator register = check(service, "register");
        assertEquals("platform", register.getResource());
        assertEquals("kinotic", register.getResourceId());
        assertEquals("vm_node", register.getPermissionResource());
        assertEquals("can_register_node", register.getPermission());

        AuthzCheckC3Decorator heartbeat = check(service, "heartbeat");
        assertEquals("vm_node", heartbeat.getResource());
        assertEquals("{registration.id}", heartbeat.getResourceId());

        AuthzCheckC3Decorator delete = check(service, "deleteByVmNodeId");
        assertEquals("{vmNodeId}", delete.getResourceId());
        assertEquals("can_delete", delete.getPermission());
    }

    @Test
    public void aServiceNamingItsObjectChecksEveryFunctionOnIt() {
        ServiceDefinition service = convert(TestMemberService.class);

        assertEquals("{@organizationId}", service.findDecorator(AuthzResourceC3Decorator.class).getResourceId());
        AuthzCheckC3Decorator findMembers = check(service, "findMembers");
        assertEquals("organization", findMembers.getResource());
        assertEquals("{@organizationId}", findMembers.getResourceId());
        assertEquals("organization", findMembers.getPermissionResource());
        assertEquals("can_view_members", findMembers.getPermission());
        assertFalse(findMembers.isConsistent());
        // an argument with an id, and a create, are checked on the service's object too
        assertEquals("{@organizationId}", check(service, "saveRole").getResourceId());
        assertEquals("organization", check(service, "saveRole").getResource());
        assertEquals("{@organizationId}", check(service, "createInvite").getResourceId());
        assertEquals("organization", check(service, "createInvite").getPermissionResource());

        // a function naming its own object is checked on it, not on the service's
        AuthzCheckC3Decorator findProject = check(service, "findProject");
        assertEquals("project", findProject.getResource());
        assertEquals("{projectId}", findProject.getResourceId());
        assertEquals("project", findProject.getPermissionResource());
    }

    @Test
    public void aConsistentCheckIsMarked() {
        AuthzCheckC3Decorator remove = check(convert(TestMemberService.class), "removeMember");

        assertEquals("can_manage_members", remove.getPermission());
        assertEquals("{@organizationId}", remove.getResourceId());
        assertTrue(remove.isConsistent());
        assertFalse(check(convert(TestProjectService.class), "save").isConsistent());
    }

    @Test
    public void anUncheckedFunctionCarriesTheMarkAndNoCheck() {
        ServiceDefinition service = convert(TestMemberService.class);

        AuthzCheckC3Decorator mark = check(service, "listAccessible");
        assertTrue(mark.isUnchecked());
        assertNull(mark.getPermission());
        assertNull(mark.getResource());
        assertEquals(List.of("type", "permission"),
                     function(service, "listAccessible").getParameters().stream().map(ParameterDefinition::getName).toList());
    }

    @Test
    public void aServiceMarkedUncheckedCarriesTheMarkOnEveryFunction() {
        ServiceDefinition service = convert(TestUncheckedService.class);

        assertNull(service.findDecorator(AuthzResourceC3Decorator.class));
        for (FunctionDefinition function : service.getFunctions()) {
            assertTrue(function.findDecorator(AuthzCheckC3Decorator.class).isUnchecked(), function.getName());
        }
    }

    @Test
    public void aServiceMarkedUncheckedBesideAResourceRejectsTheService() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> convert(TestUncheckedResourceService.class));

        assertTrue(e.getMessage().contains("@AuthzUnchecked"), e.getMessage());
        assertTrue(e.getMessage().contains("TestUncheckedResourceService"), e.getMessage());
    }

    @Test
    public void aCheckOnAServiceDeclaringNoResourceRejectsTheService() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> convert(TestResourcelessCheckService.class));

        assertTrue(e.getMessage().contains("declares no resource"), e.getMessage());
        assertTrue(e.getMessage().contains("findById"), e.getMessage());
    }

    @Test
    public void uncheckedBesideACheckRejectsTheService() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> convert(TestContradictoryService.class));

        assertTrue(e.getMessage().contains("unchecked"));
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
        assertEquals("platform:kinotic", check(service, "deployWorkload").getResource() + ":" + check(service, "deployWorkload").getResourceId());
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
