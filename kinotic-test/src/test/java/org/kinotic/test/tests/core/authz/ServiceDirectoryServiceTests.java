package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.app.api.services.ServiceDirectoryService;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectKind;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.security.identity.MachineKind;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.schema.AnyC3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzRoleDeclaration;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.security.ApplicationAccessService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies an application's own service is checked as the platform's are: a runtime of the organization is
 * refused the directory service until granted the runtime role on the application, then registers a declared
 * service in the application's zone, which the directory stores for the application with its checks derived;
 * the application's store reconciles to a model carrying the service's type and roles; and an end user is
 * refused the service until granted the type's viewer role on its tenant, admitted after, and refused the
 * function needing the declared permission until granted the declared role. A service outside the
 * application's zone, one of an application the organization has none of, and one whose definition does not
 * derive are refused.
 */
@SpringBootTest
public class ServiceDirectoryServiceTests extends KinoticTestBase {

    private static final String DIRECTORY_SERVICE = DomainUtil.APP_API_ZONE + "~org.kinotic.app.api.services.ServiceDirectoryService";
    private static final String ZONE = DomainUtil.applicationZone(TEST_ORG_ID, TEST_APP_ID);
    private static final String NAMESPACE = "com.acme.reports";
    private static final String VERSION = "1.0.0";

    @Autowired
    private ServiceDirectoryService directoryService;

    @Autowired
    private ApplicationAccessService access;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ParticipantIdentityService identityService;

    @Autowired
    private RelationshipService relationships;

    @Autowired
    private ServiceDirectory serviceDirectory;

    @Test
    public void aRuntimeRegistersAServiceTheStoreReconcilesToAndTheGatewayChecksAgainst() throws Exception {
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(TEST_APP_ID, "Sample application", null)));
        MachineParticipantIdentity runtime = runtime();
        Participant runtimeCaller = machine(runtime.getId());
        // a type of its own per run, so the store is seen taking the service up
        String type = "report" + suffix();
        String name = "ReportService" + suffix();
        String service = ZONE + "~" + NAMESPACE + "." + name;
        ServiceDirectoryEntry entry = entry(TEST_APP_ID, ZONE, reports(name, type));

        // the gateway refuses a runtime the application has not granted the role to
        assertRefused(DIRECTORY_SERVICE, "register", runtimeCaller, List.of(entry),
                      AuthzUtil.permissionName(AuthzUtil.APPLICATION_TYPE, ServiceDirectoryService.CAN_REGISTER_SERVICES) + " on application:" + DomainUtil.authzApplicationId(TEST_ORG_ID, TEST_APP_ID));
        await(relationships.bind(AuthzStoreService.PLATFORM, AuthzUtil.APPLICATION_RUNTIME_ROLE,
                                 AuthzUtil.object(AuthzUtil.USER_TYPE, runtime.getId()), AuthzUtil.object(AuthzUtil.APPLICATION_TYPE, DomainUtil.authzApplicationId(TEST_ORG_ID, TEST_APP_ID))));
        assertTrue(awaitUntil(() -> admitted(DIRECTORY_SERVICE, "register", runtimeCaller, List.of(entry))), "the runtime was never admitted");

        await(runAs(runtimeCaller, () -> directoryService.register(entry)));

        // stored for the application, with the checks derived as a Java interface's would be
        ServiceDirectoryEntry stored = await(serviceDirectory.findEntry(service));
        assertNotNull(stored, "the service never reached the directory");
        assertEquals(TEST_ORG_ID, stored.getOrganizationId());
        assertEquals(TEST_APP_ID, stored.getApplicationId());
        assertEquals(ZONE, stored.getZone());
        assertEquals(VERSION, stored.getVersion());
        assertTrue(stored.isAdvertised());
        AuthzCheckC3Decorator generate = stored.getServiceDefinition().getFunctions().stream()
                                              .filter(function -> function.getName().equals("generate"))
                                              .findFirst().orElseThrow()
                                              .findDecorator(AuthzCheckC3Decorator.class);
        assertEquals("tenant:{@tenantId}", generate.getResource() + ":" + generate.getResourceId());
        assertEquals(type + "_can_generate", generate.getPermissionResource() + "_" + generate.getPermission());
        // the application's store takes the service up: a viewer of its type, and the role it declares
        assertTrue(awaitUntil(() -> roleIds().containsAll(List.of(AuthzUtil.roleId(type, AuthzUtil.VIEWER), type + ".generator"))),
                   "the store never ran a model carrying the service");
        RoleDefinition generator = await(runAsOrganization(() -> access.findRoles(TEST_APP_ID))).stream()
                                                                                              .filter(role -> role.id().equals(type + ".generator"))
                                                                                              .findFirst().orElseThrow();
        assertEquals(Set.of(type + "_can_generate"), generator.permissions());

        // an end user is refused until granted, and the declared permission needs the declared role
        String tenantId = "tenant-" + suffix();
        UserParticipantIdentity bob = endUser(tenantId);
        Participant caller = applicationParticipant(tenantId, bob.getId());
        Subject subject = new Subject(SubjectKind.USER, bob.getId());
        Resource tenant = new Resource(AuthzUtil.TENANT_TYPE, tenantId);
        assertRefused(service, "findReports", caller, List.of(), type + "_can_view on tenant:" + tenantId);
        await(runAsOrganization(() -> access.grant(TEST_APP_ID, subject, AuthzUtil.roleId(type, AuthzUtil.VIEWER), tenant)));
        assertTrue(awaitUntil(() -> admitted(service, "findReports", caller, List.of())), "the viewer was never admitted");
        assertRefused(service, "generate", caller, List.of("q3"), type + "_can_generate");
        await(runAsOrganization(() -> access.grant(TEST_APP_ID, subject, type + ".generator", tenant)));
        assertTrue(awaitUntil(() -> admitted(service, "generate", caller, List.of("q3"))), "the generator was never admitted");
        // an unchecked function passes on the zone
        authorize(service, "ping", caller, List.of());

        // what the directory service refuses
        assertInstanceOf(IllegalArgumentException.class, failure(runtimeCaller, () -> directoryService.register(entry(TEST_APP_ID, "app.other.crm", reports(name, type)))));
        assertInstanceOf(IllegalArgumentException.class, failure(runtimeCaller, () -> directoryService.register(entry("no-such-app", DomainUtil.applicationZone(TEST_ORG_ID, "no-such-app"), reports(name, type)))));
        ServiceDefinition underived = reports(name, type);
        underived.addFunction(function("render", null, "reportId"));
        assertInstanceOf(IllegalStateException.class, failure(runtimeCaller, () -> directoryService.register(entry(TEST_APP_ID, ZONE, underived))));
    }

    private static ServiceDirectoryEntry entry(String applicationId, String zone, ServiceDefinition definition) {
        return new ServiceDirectoryEntry()
                .setApplicationId(applicationId)
                .setZone(zone)
                .setVersion(VERSION)
                .setAdvertised(true)
                .setServiceDefinition(definition);
    }

    @Test
    public void aContractWrittenAgainUnderTheSameVersionIsCheckedAsItIsNow() throws Exception {
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(TEST_APP_ID, "Sample application", null)));
        MachineParticipantIdentity runtime = runtime();
        Participant runtimeCaller = machine(runtime.getId());
        await(relationships.bind(AuthzStoreService.PLATFORM, AuthzUtil.APPLICATION_RUNTIME_ROLE,
                                 AuthzUtil.object(AuthzUtil.USER_TYPE, runtime.getId()), AuthzUtil.object(AuthzUtil.APPLICATION_TYPE, DomainUtil.authzApplicationId(TEST_ORG_ID, TEST_APP_ID))));
        String type = "report" + suffix();
        String name = "ReportService" + suffix();
        String service = ZONE + "~" + NAMESPACE + "." + name;
        assertTrue(awaitUntil(() -> admitted(DIRECTORY_SERVICE, "register", runtimeCaller, List.of(entry(TEST_APP_ID, ZONE, reports(name, type))))),
                   "the runtime was never admitted");
        await(runAs(runtimeCaller, () -> directoryService.register(entry(TEST_APP_ID, ZONE, reports(name, type)))));
        assertTrue(awaitUntil(() -> roleIds().contains(AuthzUtil.roleId(type, AuthzUtil.VIEWER))), "the store never ran a model carrying the service");
        String tenantId = "tenant-" + suffix();
        UserParticipantIdentity bob = endUser(tenantId);
        Participant caller = applicationParticipant(tenantId, bob.getId());
        // the authorizer reads the contract and keeps it: generate is checked
        assertRefused(service, "generate", caller, List.of("q1"), type + "_can_generate on tenant:" + tenantId);

        // the runtime registers the service again at the same version, serving generate unchecked
        await(runAs(runtimeCaller, () -> directoryService.register(entry(TEST_APP_ID, ZONE, reports(name, type, false)))));
        assertTrue(awaitUntil(() -> admitted(service, "generate", caller, List.of("q1"))), "the contract written again was checked as before");
    }

    private static ServiceDefinition reports(String name, String type) {
        return reports(name, type, true);
    }

    // The reports service of the given type: generate checked for the declared permission, or served unchecked
    private static ServiceDefinition reports(String name, String type, boolean generateChecked) {
        AuthzResourceC3Decorator resource = new AuthzResourceC3Decorator()
                .setResourceType(type)
                .setParent(AuthzUtil.TENANT_TYPE)
                .setRoles(List.of(new AuthzRoleDeclaration().setId(type + ".generator").setPermissions(List.of("can_generate"))));
        ServiceDefinition ret = new ServiceDefinition().setNamespace(NAMESPACE).setName(name);
        ret.setDecorators(List.of(resource));
        ret.addFunction(function("findReports", null));
        ret.addFunction(function("generate", generateChecked
                ? new AuthzCheckC3Decorator().setPermission("can_generate").setResource(AuthzUtil.TENANT_TYPE)
                : new AuthzCheckC3Decorator().setUnchecked(true), "name"));
        ret.addFunction(function("ping", new AuthzCheckC3Decorator().setUnchecked(true)));
        return ret;
    }

    private static FunctionDefinition function(String name, AuthzCheckC3Decorator check, String... parameters) {
        FunctionDefinition ret = new FunctionDefinition().setName(name).setReturnType(new AnyC3Type());
        for (String parameter : parameters) {
            ret.addParameter(parameter, new StringC3Type());
        }
        if (check != null) {
            ret.setDecorators(List.of(check));
        }
        return ret;
    }

    private Set<String> roleIds() throws Exception {
        Set<String> ret = new HashSet<>();
        for (RoleDefinition role : await(runAsOrganization(() -> access.findRoles(TEST_APP_ID)))) {
            ret.add(role.id());
        }
        return ret;
    }

    private MachineParticipantIdentity runtime() throws Exception {
        MachineParticipantIdentity machine = new MachineParticipantIdentity();
        machine.setMachineKind(MachineKind.APP_RUNTIME)
               .setDisplayName("runtime " + suffix())
               .setOrganizationId(TEST_ORG_ID);
        return await(identityService.createMachine(machine)).machine();
    }

    private UserParticipantIdentity endUser(String tenantId) throws Exception {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail("directory-user-" + suffix() + "@kinotic.test");
        user.setDisplayName("Directory User");
        user.setOrganizationId(TEST_ORG_ID);
        user.setApplicationId(TEST_APP_ID);
        user.setTenantId(tenantId);
        return await(identityService.createUser(user, "Directory-1"));
    }

    private static Participant machine(String id) {
        return new DefaultOrganizationParticipant(id, TEST_ORG_ID,
                                                  Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_MACHINE),
                                                  List.of());
    }

    private Throwable failure(Participant caller, Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAs(caller, call::get))).getCause();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
