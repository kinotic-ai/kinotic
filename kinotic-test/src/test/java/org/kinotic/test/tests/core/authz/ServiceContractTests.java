package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectKind;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.security.identity.MachineKind;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.services.ServiceContractService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RequestAuthorizer;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.directory.AuthzCheckDeclaration;
import org.kinotic.idl.api.directory.AuthzResourceDeclaration;
import org.kinotic.idl.api.directory.FunctionContract;
import org.kinotic.idl.api.directory.ServiceContract;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzRoleDeclaration;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.security.ApplicationAccessService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
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
 * refused the contract service until granted the runtime role on the application, then publishes a declared
 * contract in the application's zone, which the directory stores for the application with its checks derived;
 * the application's store reconciles to a model carrying the service's type and roles; and an end user is
 * refused the service until granted the type's viewer role on its tenant, admitted after, and refused the
 * function needing the declared permission until granted the declared role. A contract outside the
 * application's zone, one for an application the organization has none of, and one that does not convert are
 * refused.
 */
@SpringBootTest
public class ServiceContractTests extends KinoticTestBase {

    private static final String CONTRACT_SERVICE = DomainUtil.APP_API_ZONE + "~org.kinotic.domain.api.services.ServiceContractService";
    private static final String ZONE = DomainUtil.applicationZone(TEST_ORG_ID, TEST_APP_ID);
    private static final String NAMESPACE = "com.acme.reports";
    private static final String VERSION = "1.0.0";

    @Autowired
    private ServiceContractService contracts;

    @Autowired
    private RequestAuthorizer authorizer;

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

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    public void aRuntimePublishesAContractTheStoreReconcilesToAndTheGatewayChecksAgainst() throws Exception {
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(TEST_APP_ID, "Sample application", null)));
        MachineParticipantIdentity runtime = runtime();
        Participant runtimeCaller = machine(runtime.getId());
        // a type of its own per run, so the store is seen taking the contract up
        String type = "report" + suffix();
        String name = "ReportService" + suffix();
        String service = ZONE + "~" + NAMESPACE + "." + name;
        ServiceContract contract = contract(name, type, ZONE);

        // the gateway refuses a runtime the application has not granted the role to
        assertRefused(CONTRACT_SERVICE, "register", runtimeCaller, List.of(TEST_APP_ID, contract),
                      AuthzUtil.permissionName(AuthzUtil.APPLICATION_TYPE, ServiceContractService.CAN_PUBLISH_SERVICES) + " on application:" + TEST_APP_ID);
        await(relationships.bind(AuthzStoreService.PLATFORM, ServiceContractService.RUNTIME_ROLE,
                                 AuthzUtil.object(AuthzUtil.USER_TYPE, runtime.getId()), AuthzUtil.object(AuthzUtil.APPLICATION_TYPE, TEST_APP_ID)));
        assertTrue(awaitUntil(() -> admitted(CONTRACT_SERVICE, "register", runtimeCaller, List.of(TEST_APP_ID, contract))), "the runtime was never admitted");

        await(runAs(runtimeCaller, () -> contracts.register(TEST_APP_ID, contract)));

        // stored for the application, with the checks derived as a Java interface's would be
        ServiceDirectoryEntry entry = await(serviceDirectory.findEntry(service));
        assertNotNull(entry, "the contract never reached the directory");
        assertEquals(TEST_ORG_ID, entry.getOrganizationId());
        assertEquals(TEST_APP_ID, entry.getApplicationId());
        assertEquals(ZONE, entry.getZone());
        AuthzCheckC3Decorator generate = entry.getServiceDefinition().getFunctions().stream()
                                             .filter(function -> function.getName().equals("generate"))
                                             .findFirst().orElseThrow()
                                             .findDecorator(AuthzCheckC3Decorator.class);
        assertEquals("tenant:{@tenantId}", generate.getResource() + ":" + generate.getObjectId());
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
        // a zone-only function passes on the zone
        authorize(service, "ping", caller, List.of());

        // what the contract service refuses
        assertInstanceOf(IllegalArgumentException.class, failure(runtimeCaller, () -> contracts.register(TEST_APP_ID, contract(name, type, "app.other.crm"))));
        assertInstanceOf(IllegalArgumentException.class, failure(runtimeCaller, () -> contracts.register("no-such-app", contract(name, type, DomainUtil.applicationZone(TEST_ORG_ID, "no-such-app")))));
        ServiceContract unconvertible = new ServiceContract(NAMESPACE, name, VERSION, ZONE, contract.resource(),
                                                            List.of(new FunctionContract("render", List.of("reportId"), null)));
        assertInstanceOf(IllegalStateException.class, failure(runtimeCaller, () -> contracts.register(TEST_APP_ID, unconvertible)));
    }

    private static ServiceContract contract(String name, String type, String zone) {
        AuthzResourceDeclaration resource = new AuthzResourceDeclaration(type, AuthzUtil.TENANT_TYPE, null, null,
                                                                         List.of(new AuthzRoleDeclaration().setId(type + ".generator").setPermissions(List.of("can_generate"))));
        return new ServiceContract(NAMESPACE, name, VERSION, zone, resource,
                                   List.of(new FunctionContract("findReports", List.of(), null),
                                           new FunctionContract("generate", List.of("name"),
                                                                new AuthzCheckDeclaration("can_generate", AuthzUtil.TENANT_TYPE, null, null, false, false)),
                                           new FunctionContract("ping", List.of(), new AuthzCheckDeclaration(null, null, null, null, true, false))));
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
        user.setEmail("contract-user-" + suffix() + "@kinotic.test");
        user.setDisplayName("Contract User");
        user.setOrganizationId(TEST_ORG_ID);
        user.setApplicationId(TEST_APP_ID);
        user.setTenantId(tenantId);
        return await(identityService.createUser(user, "Contract-1"));
    }

    private static Participant machine(String id) {
        return new DefaultOrganizationParticipant(id, TEST_ORG_ID,
                                                  Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_MACHINE),
                                                  List.of());
    }

    private void authorize(String service, String function, Participant caller, Object arguments) throws Exception {
        await(authorizer.authorize(cri(service, function), caller, EventConstants.CONTENT_TYPE_JSON,
                                   jsonMapper.writeValueAsString(arguments).getBytes(StandardCharsets.UTF_8)));
    }

    private boolean admitted(String service, String function, Participant caller, Object arguments) throws Exception {
        boolean ret;
        try {
            authorize(service, function, caller, arguments);
            ret = true;
        } catch (ExecutionException refused) {
            assertInstanceOf(AuthorizationException.class, refused.getCause());
            ret = false;
        }
        return ret;
    }

    private void assertRefused(String service, String function, Participant caller, Object arguments, String naming) throws Exception {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> authorize(service, function, caller, arguments));
        AuthorizationException refused = assertInstanceOf(AuthorizationException.class, failure.getCause());
        assertTrue(refused.getMessage().contains(naming), refused.getMessage());
    }

    private Throwable failure(Participant caller, Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAs(caller, call::get))).getCause();
    }

    private CRI cri(String service, String function) throws Exception {
        ServiceDirectoryEntry entry = await(serviceDirectory.findEntry(service));
        assertNotNull(entry, "the directory holds no contract for " + service);
        return CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME + "://" + TEST_ORG_ID + "@" + service + "/" + function + "#" + entry.getVersion());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
