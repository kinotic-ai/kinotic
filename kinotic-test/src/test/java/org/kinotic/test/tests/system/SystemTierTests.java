package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectKind;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.model.security.identity.MachineKind;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultSystemParticipant;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.model.telemetry.ServerLogQuery;
import org.kinotic.management.api.services.telemetry.LogService;
import org.kinotic.system.api.model.workload.VmNodeRegistration;
import org.kinotic.system.api.services.SystemAccessService;
import org.kinotic.system.api.services.workload.VmNodeOrchestrationService;
import org.kinotic.system.api.services.workload.VmNodeService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.system.NodeFixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kinotic.authz.api.services.AuthzStoreService.PLATFORM;

/**
 * Verifies the system tier is authorized as the gateway authorizes it, against the platform store: the
 * platform's seeded administrator and vm-manager are bound once; a support operator reads every organization,
 * the cluster and the servers' logs and is refused the platform's writes; an operator manages machines and
 * workloads and is refused a node's reports; a machine granted the registrar role registers a node, reports
 * for that node alone, and loses its agent grant when the node is deregistered; a platform grant names only
 * the platform's own staff; and the telemetry tenant a platform participant may read follows its grant.
 */
@SpringBootTest
public class SystemTierTests extends KinoticTestBase {

    private static final String ORGANIZATION_SERVICE = "system-api~org.kinotic.system.api.services.SystemOrganizationService";
    private static final String MEMBER_SERVICE = "system-api~org.kinotic.system.api.services.SystemMemberService";
    private static final String CLUSTER_SERVICE = "system-api~org.kinotic.system.api.services.KinoticClusterInfoService";
    private static final String ACCESS_SERVICE = "system-api~org.kinotic.system.api.services.SystemAccessService";
    private static final String NODE_SERVICE = "system-api~org.kinotic.system.api.services.workload.VmNodeOrchestrationService";
    private static final String NODE_RECORD_SERVICE = "system-api~org.kinotic.system.api.services.workload.VmNodeService";
    private static final String WORKLOAD_SERVICE = "system-api~org.kinotic.system.api.services.workload.WorkloadOrchestrationService";
    private static final String LOG_SERVICE = "management-api~org.kinotic.management.api.services.telemetry.LogService";
    // the operator and the vm-manager V2__kinotic_test_users seeds
    private static final String SEEDED_ADMIN_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SEEDED_VM_MANAGER_ID = "00000000-0000-0000-0000-000000000011";
    private static final Map<String, Object> PAGE = Map.of("pageNumber", 0, "pageSize", 10);

    @Autowired
    private SystemAccessService access;

    @Autowired
    private ParticipantIdentityService identityService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private VmNodeOrchestrationService nodeOrchestration;

    @Autowired
    private VmNodeService vmNodeService;

    @Autowired
    private LogService logService;

    @Autowired
    private RelationshipService relationships;

    @Autowired
    private AuthzStoreService storeService;

    @Autowired
    private AuthzStoreRepository stores;

    @Autowired
    private AuthzModelGenerator modelGenerator;

    @Autowired
    private ServiceDirectory serviceDirectory;

    private final List<String> nodes = new ArrayList<>();

    @BeforeEach
    public void awaitPlatformModel() throws Exception {
        AuthzModel model = modelGenerator.platformModel(await(serviceDirectory.findSystemDefinitions()));
        assertTrue(awaitUntil(() -> reconciledTo(model.hash())), "the platform store never reconciled to the directory's model");
    }

    @AfterEach
    public void removeNodes() throws Exception {
        for (String nodeId : nodes) {
            if (await(vmNodeService.findById(nodeId)) != null) {
                await(vmNodeService.deleteById(nodeId));
            }
        }
        nodes.clear();
    }

    @Test
    public void theSeededStaffAreBoundOnce() throws Exception {
        // the system server sweeps the platform once the store runs a model, shortly after the servers start
        assertTrue(awaitUntil(() -> grantsOf(SEEDED_ADMIN_ID, AuthzUtil.PLATFORM_ADMIN_ROLE) == 1), "the seeded operator never administered the platform");
        assertTrue(awaitUntil(() -> grantsOf(SEEDED_VM_MANAGER_ID, VmNodeOrchestrationService.REGISTRAR_ROLE) == 1), "the seeded vm-manager never registered nodes");

        // the administrator holds everything on the platform and inside it
        Participant admin = operator(SEEDED_ADMIN_ID);
        authorize(MEMBER_SERVICE, "createMachine", admin, List.of("a machine"));
        authorize(ACCESS_SERVICE, "grant", admin, List.of(new Subject(SubjectKind.USER, SEEDED_ADMIN_ID), AuthzUtil.PLATFORM_SUPPORT_ROLE));
        authorize(ORGANIZATION_SERVICE, "findMembers", admin, Arrays.asList(organization().getId(), null, PAGE));
        authorize(NODE_SERVICE, "registerNode", admin, List.of(NodeFixtures.registration("any-node", 1, 1024, 1024)));
    }

    @Test
    public void supportReadsThePlatformAndIsRefusedItsWrites() throws Exception {
        UserParticipantIdentity support = operatorIdentity();
        Subject subject = new Subject(SubjectKind.USER, support.getId());
        Grant grant = await(access.grant(subject, AuthzUtil.PLATFORM_SUPPORT_ROLE));
        Organization organization = organization();
        Participant caller = operator(support.getId());

        assertTrue(awaitUntil(() -> admitted(ORGANIZATION_SERVICE, "findOrganizations", caller, List.of(PAGE))), "support never saw the organizations");
        // a read of one organization is checked on it, which the platform grant reaches through the graph
        authorize(ORGANIZATION_SERVICE, "findApplications", caller, List.of(organization.getId(), PAGE));
        authorize(ORGANIZATION_SERVICE, "findMembers", caller, Arrays.asList(organization.getId(), null, PAGE));
        authorize(CLUSTER_SERVICE, "getClusterInfo", caller, List.of());
        // a check on the caller's organization, which an operator has none of, is made on the platform
        authorize(LOG_SERVICE, "serverHistory", caller, List.of(Map.of("telemetryServiceName", "kinotic-test")));
        assertRefused(MEMBER_SERVICE, "createMachine", caller, List.of("a machine"), "platform_can_manage_machines");
        assertRefused(WORKLOAD_SERVICE, "stopWorkload", caller, List.of("wl-1"), "platform_can_manage_workloads");
        assertRefused(ACCESS_SERVICE, "grant", caller, List.of(subject, AuthzUtil.PLATFORM_SUPPORT_ROLE), "platform_can_manage_access");

        AccessExplanation explanation = await(access.explain(subject, "can_view_cluster"));
        assertTrue(explanation.allowed());
        assertEquals(List.of(grant.id()), explanation.through().stream().map(Grant::id).toList());
        assertFalse(await(access.explain(subject, "can_manage_machines")).allowed());
        assertTrue(await(access.findGrants()).contains(grant));

        await(access.revoke(grant.id()));
        assertTrue(awaitUntil(() -> !admitted(CLUSTER_SERVICE, "getClusterInfo", caller, List.of())), "support still read the cluster after its revocation");
        assertFalse(await(access.findGrants()).contains(grant));
    }

    @Test
    public void anOperatorRunsThePlatformAndIsRefusedANodesReports() throws Exception {
        UserParticipantIdentity operator = operatorIdentity();
        await(access.grant(new Subject(SubjectKind.USER, operator.getId()), AuthzUtil.PLATFORM_OPERATOR_ROLE));
        Participant caller = operator(operator.getId());

        assertTrue(awaitUntil(() -> admitted(WORKLOAD_SERVICE, "stopWorkload", caller, List.of("wl-1"))), "the operator never managed workloads");
        authorize(MEMBER_SERVICE, "createMachine", caller, List.of("a machine"));
        authorize(MEMBER_SERVICE, "rotateSecret", caller, List.of(SEEDED_VM_MANAGER_ID));
        authorize(ORGANIZATION_SERVICE, "findOrganizations", caller, List.of(PAGE));
        // a listing of nodes with room is a read of nodes, checked on the platform
        authorize(NODE_SERVICE, "findAvailableNode", caller, List.of(1, 1024, 1024));
        assertRefused(NODE_SERVICE, "registerNode", caller, List.of(NodeFixtures.registration("any-node", 1, 1024, 1024)), "vm_node_can_register_node");
    }

    @Test
    public void aRegistrarRegistersANodeAndReportsForItAlone() throws Exception {
        MachineParticipantIdentity registrar = machine("tier registrar");
        MachineParticipantIdentity other = machine("tier bystander");
        UserParticipantIdentity operator = operatorIdentity();
        await(access.grant(new Subject(SubjectKind.USER, registrar.getId()), VmNodeOrchestrationService.REGISTRAR_ROLE));
        await(access.grant(new Subject(SubjectKind.USER, operator.getId()), AuthzUtil.PLATFORM_OPERATOR_ROLE));
        Participant registrarCaller = machineParticipant(registrar.getId());
        Participant otherCaller = machineParticipant(other.getId());
        VmNodeRegistration node = NodeFixtures.registration("tier-" + suffix(), 4, 4096, 10240);
        nodes.add(node.getId());
        String nodeObject = AuthzUtil.object(VmNodeService.RESOURCE_TYPE, node.getId());

        assertTrue(awaitUntil(() -> admitted(NODE_SERVICE, "registerNode", registrarCaller, List.of(node))), "the registrar never registered");
        assertRefused(NODE_SERVICE, "registerNode", otherCaller, List.of(node), "vm_node_can_register_node");
        // before the registration nothing reports for the node, the registrar included
        assertRefused(NODE_SERVICE, "heartbeat", registrarCaller, List.of(node.getId(), List.of()), "vm_node_can_report");

        // registered by the machine, the node is on the platform and the machine is its agent
        await(runAs(registrarCaller, () -> nodeOrchestration.registerNode(node)));
        assertTrue(awaitUntil(() -> admitted(NODE_SERVICE, "heartbeat", registrarCaller, List.of(node.getId(), List.of()))), "the agent never reported");
        authorize(NODE_SERVICE, "reportWorkloadStatus", registrarCaller, List.of(node.getId(), List.of()));
        authorize(NODE_SERVICE, "deregisterNode", registrarCaller, List.of(node.getId()));
        assertRefused(NODE_SERVICE, "heartbeat", registrarCaller, List.of("tier-other-" + suffix(), List.of()), "vm_node_can_report");
        assertRefused(NODE_SERVICE, "heartbeat", otherCaller, List.of(node.getId(), List.of()), "vm_node_can_report");
        // the platform's administrator reaches the node through the platform, an operator reads it and reports nothing
        assertTrue(awaitUntil(() -> admitted(NODE_SERVICE, "heartbeat", operator(SEEDED_ADMIN_ID), List.of(node.getId(), List.of()))), "the administrator never reached the node");
        assertTrue(awaitUntil(() -> admitted(NODE_RECORD_SERVICE, "findHistory", operator(operator.getId()), List.of(node.getId(), PAGE))), "the operator never read the node");
        assertRefused(NODE_SERVICE, "heartbeat", operator(operator.getId()), List.of(node.getId(), List.of()), "vm_node_can_report");
        List<Grant> grants = await(relationships.findGrants(PLATFORM, nodeObject));
        assertEquals(1, grants.size());
        assertEquals(VmNodeOrchestrationService.AGENT_ROLE, grants.getFirst().roleId());
        assertEquals(new Subject(SubjectKind.USER, registrar.getId()), grants.getFirst().subject());

        // a node registering again binds nothing twice
        await(runAs(registrarCaller, () -> nodeOrchestration.registerNode(node)));
        assertEquals(grants, await(relationships.findGrants(PLATFORM, nodeObject)));

        // the deregistration's finalizer takes the node out of the graph with its agent's grant
        await(nodeOrchestration.deregisterNode(node.getId()));
        assertTrue(awaitUntil(() -> await(vmNodeService.findById(node.getId())) == null), "the node was never finalized");
        assertTrue(awaitUntil(() -> await(relationships.read(PLATFORM, nodeObject)).isEmpty()), "the node is still in the graph");
        assertTrue(await(relationships.read(PLATFORM, AuthzUtil.object(AuthzUtil.ROLE_BINDING_TYPE, grants.getFirst().id()))).isEmpty());
    }

    @Test
    public void aPlatformGrantNamesOnlyThePlatformsStaff() throws Exception {
        UserParticipantIdentity member = member();
        UserParticipantIdentity operator = operatorIdentity();

        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> access.grant(new Subject(SubjectKind.USER, member.getId()), AuthzUtil.PLATFORM_SUPPORT_ROLE)));
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> access.grant(new Subject(SubjectKind.GROUP, operator.getId()), AuthzUtil.PLATFORM_SUPPORT_ROLE)));
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> access.grant(new Subject(SubjectKind.USER, operator.getId()), "project.editor-of-nothing")));
        assertInstanceOf(IllegalArgumentException.class, failure(() -> access.revoke("no-such-grant")));
        // the roles a platform grant can name are the model's, the staff roles among them
        List<String> roles = await(access.findRoles()).stream().map(role -> role.id()).toList();
        assertTrue(roles.containsAll(List.of(AuthzUtil.PLATFORM_ADMIN_ROLE, AuthzUtil.PLATFORM_OPERATOR_ROLE, AuthzUtil.PLATFORM_SUPPORT_ROLE,
                                             VmNodeOrchestrationService.REGISTRAR_ROLE, VmNodeOrchestrationService.AGENT_ROLE)), roles.toString());
    }

    @Test
    public void theTelemetryTenantFollowsThePlatformGrant() throws Exception {
        UserParticipantIdentity newcomer = operatorIdentity();
        UserParticipantIdentity support = operatorIdentity();
        await(access.grant(new Subject(SubjectKind.USER, support.getId()), AuthzUtil.PLATFORM_SUPPORT_ROLE));
        ServerLogQuery query = new ServerLogQuery().setTelemetryServiceName("kinotic-test").setStart(0).setEnd(1).setLimit(1);

        assertInstanceOf(AuthorizationException.class, failure(() -> runAs(operator(newcomer.getId()), () -> logService.serverHistory(query))));
        assertInstanceOf(AuthorizationException.class, failure(() -> runAsOrganization(() -> logService.serverHistory(query))));
        // the platform's tenant is read for support; whether the log store answers is not the tenant's to decide
        assertTrue(awaitUntil(() -> !(failure(() -> runAs(operator(support.getId()), () -> logService.serverHistory(query))) instanceof AuthorizationException)),
                   "support was never admitted to the platform's logs");
    }

    // How many grants of the role the platform holds for the subject
    private long grantsOf(String subjectId, String roleId) throws Exception {
        Subject subject = new Subject(SubjectKind.USER, subjectId);
        return await(access.findGrants()).stream().filter(grant -> roleId.equals(grant.roleId()) && subject.equals(grant.subject())).count();
    }

    private static Throwable failure(java.util.function.Supplier<Future<?>> call) {
        Throwable ret;
        try {
            await(call.get());
            ret = null;
        } catch (ExecutionException e) {
            ret = e.getCause();
        } catch (Exception e) {
            ret = e;
        }
        return ret;
    }

    private static Participant operator(String id) {
        return new DefaultSystemParticipant(id, Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER), List.of());
    }

    private static Participant machineParticipant(String id) {
        return new DefaultSystemParticipant(id, Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_MACHINE), List.of());
    }

    // A platform operator is a user with no organization
    private UserParticipantIdentity operatorIdentity() throws Exception {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail("operator-" + suffix() + "@kinotic.test");
        user.setDisplayName("Tier Operator");
        return await(identityService.createUser(user, "Operator-1"));
    }

    private UserParticipantIdentity member() throws Exception {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail("member-" + suffix() + "@kinotic.test");
        user.setDisplayName("Tier Member");
        user.setOrganizationId(TEST_ORG_ID);
        return await(identityService.createUser(user, "Member-1"));
    }

    // A platform machine is a machine with no organization, as the console provisions one
    private MachineParticipantIdentity machine(String displayName) throws Exception {
        MachineParticipantIdentity machine = new MachineParticipantIdentity();
        machine.setMachineKind(MachineKind.CLIENT).setDisplayName(displayName);
        return await(identityService.createMachine(machine)).machine();
    }

    private Organization organization() throws Exception {
        Organization organization = new Organization();
        organization.setName("Tier " + suffix()).setDescription("system tier");
        return await(organizationService.createSync(organization));
    }

    private boolean reconciledTo(String hash) throws Exception {
        AuthzStore store = await(stores.findById(AuthzStore.PLATFORM));
        ReconcileState<AuthzModelRevision> state = store.getState();
        return state.isReconciled() && state.getObserved() != null && hash.equals(state.getObserved().hash());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
