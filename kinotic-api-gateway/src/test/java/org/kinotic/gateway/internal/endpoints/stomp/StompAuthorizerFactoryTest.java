package org.kinotic.gateway.internal.endpoints.stomp;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.domain.api.security.DefaultApplicationParticipant;
import org.kinotic.domain.api.security.DefaultOrganizationParticipant;
import org.kinotic.domain.api.security.DefaultSystemParticipant;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the zone routing rules enforced per participant type: the verb x participant x zone
 * matrix, dot-boundary prefix safety, and rejection of ids that cannot form a valid zone.
 */
public class StompAuthorizerFactoryTest {

    private static final String REPLY_TO_ID = "reply-to-1";

    private final StompAuthorizerFactory factory = new StompAuthorizerFactory();

    private StompAuthorizer applicationAuthorizer(String organizationId, String applicationId) {
        DefaultApplicationParticipant participant = DefaultApplicationParticipant.builder()
                                                                                 .id("app-user")
                                                                                 .organizationId(organizationId)
                                                                                 .applicationId(applicationId)
                                                                                 .metadata(Map.of())
                                                                                 .roles(List.of())
                                                                                 .build();
        return factory.create(new ConnectedInfo(participant, REPLY_TO_ID));
    }

    private StompAuthorizer organizationAuthorizer(String organizationId) {
        DefaultOrganizationParticipant participant = DefaultOrganizationParticipant.builder()
                                                                                   .id("org-user")
                                                                                   .organizationId(organizationId)
                                                                                   .metadata(Map.of())
                                                                                   .roles(List.of())
                                                                                   .build();
        return factory.create(new ConnectedInfo(participant, REPLY_TO_ID));
    }

    private StompAuthorizer systemAuthorizer() {
        DefaultSystemParticipant participant = DefaultSystemParticipant.builder()
                                                                       .id("system-user")
                                                                       .metadata(Map.of())
                                                                       .roles(List.of())
                                                                       .build();
        return factory.create(new ConnectedInfo(participant, REPLY_TO_ID));
    }

    @Test
    public void applicationParticipantSendRules() {
        StompAuthorizer authorizer = applicationAuthorizer("acme-org", "orders-app");

        // the app_api data plane and the app's own zone, including app declared sub zones
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app_api.org.kinotic.persistence.api.services.JsonEntitiesRepository/save#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app.OrderService/create#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app.billing.InvoiceService/pay#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("stream://app.acme-org.orders-app.OrderEvents")));

        // the organization management surface, other applications, other organizations, the
        // platform internals, and legacy un-zoned addresses
        assertFalse(authorizer.sendAllowed(CRI.create("srv://os_api.org.kinotic.os.api.services.iam.MemberService/inviteMember#1.0.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.acme-org.other-app.OrderService/create#1.0.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.other-org.orders-app.OrderService/create#1.0.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("srv://system.org.kinotic.orchestrator.api.workload.WorkloadOrchestrationService/startWorkload#0.1.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("srv://org.kinotic.os.api.services.iam.MemberService/findMembers#1.0.0")));
    }

    @Test
    public void slugifiedIdsWithUnderscoresFormValidZones() {
        StompAuthorizer authorizer = applicationAuthorizer("acme_corp", "orders_app");

        assertTrue(authorizer.sendAllowed(CRI.create("srv://app.acme_corp.orders_app.OrderService/create#1.0.0")));
        assertTrue(authorizer.subscribeAllowed(CRI.create("srv://app.acme_corp.orders_app.OrderService#1.0.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.acme_corp.other_app.OrderService/create#1.0.0")));
    }

    @Test
    public void applicationZoneMatchingRespectsDotBoundaries() {
        StompAuthorizer authorizer = applicationAuthorizer("acme-org", "orders-app");

        // a zone that merely starts with the app's zone text must not match
        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app-2.OrderService/create#1.0.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.acme-org-2.orders-app.OrderService/create#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app-2.OrderService#1.0.0")));
    }

    @Test
    public void applicationParticipantHostsOnlyItsOwnZone() {
        StompAuthorizer authorizer = applicationAuthorizer("acme-org", "orders-app");

        assertTrue(authorizer.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app.OrderService#1.0.0")));
        assertTrue(authorizer.subscribeAllowed(CRI.create("srv://node1@app.acme-org.orders-app.OrderService#1.0.0")));
        assertTrue(authorizer.subscribeAllowed(CRI.create("stream://app.acme-org.orders-app.OrderEvents")));

        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app_api.org.kinotic.persistence.api.services.JsonEntitiesRepository#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://os_api.org.kinotic.os.api.services.iam.MemberService#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://system.kinotic-ai.vm-manager.VmManager#0.1.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app.acme-org.other-app.OrderService#1.0.0")));
    }

    @Test
    public void organizationParticipantReachesTheOsApiAndAppApiZones() {
        StompAuthorizer authorizer = organizationAuthorizer("acme-org");

        // the management surface and the data plane
        assertTrue(authorizer.sendAllowed(CRI.create("srv://os_api.org.kinotic.persistence.api.services.EntityDefinitionService/publish#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://os_api.org.kinotic.os.api.services.iam.MemberService/findMembers#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app_api.org.kinotic.persistence.api.services.JsonEntitiesRepository/save#1.0.0")));

        // not an application's own services, the platform internals, and hosts nothing
        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app.OrderService/create#1.0.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("srv://system.org.kinotic.os.api.services.LogManager/query#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://os_api.org.kinotic.os.api.services.iam.MemberService#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app_api.org.kinotic.persistence.api.services.JsonEntitiesRepository#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app.OrderService#1.0.0")));
    }

    @Test
    public void systemParticipantSendsAnywhereAndHostsPlatformZones() {
        StompAuthorizer authorizer = systemAuthorizer();

        assertTrue(authorizer.sendAllowed(CRI.create("srv://os_api.org.kinotic.os.api.services.iam.MemberService/findMembers#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app_api.org.kinotic.persistence.api.services.JsonEntitiesRepository/save#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://system.org.kinotic.orchestrator.api.workload.WorkloadOrchestrationService/startWorkload#0.1.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app.OrderService/create#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://node1@system.kinotic-ai.vm-manager.VmManager/startWorkload#0.1.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://org.kinotic.server.clienttest.SomeLegacyService/method#1.0.0")));

        assertTrue(authorizer.subscribeAllowed(CRI.create("srv://system.kinotic-ai.vm-manager.VmManager#0.1.0")));
        assertTrue(authorizer.subscribeAllowed(CRI.create("srv://node1@system.kinotic-ai.vm-manager.VmManager#0.1.0")));
        assertTrue(authorizer.subscribeAllowed(CRI.create("srv://os_api.org.kinotic.some.PlatformService#1.0.0")));
        assertTrue(authorizer.subscribeAllowed(CRI.create("srv://app_api.org.kinotic.some.DataService#1.0.0")));

        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app.OrderService#1.0.0")));
    }

    @Test
    public void dottedScopesAreAuthorizedByTheirZone() {
        // a node id may be an FQDN; the scope is stripped before zone matching so it still routes
        StompAuthorizer system = systemAuthorizer();
        assertTrue(system.subscribeAllowed(CRI.create("srv://vm1.example.com@system.kinotic-ai.vm-manager.VmManager#0.1.0")));
        assertTrue(system.sendAllowed(CRI.create("srv://vm1.example.com@system.kinotic-ai.vm-manager.VmManager/start#0.1.0")));

        StompAuthorizer app = applicationAuthorizer("acme-org", "orders-app");
        assertTrue(app.subscribeAllowed(CRI.create("srv://node.1.2@app.acme-org.orders-app.OrderService#1.0.0")));
        assertFalse(app.subscribeAllowed(CRI.create("srv://node.1.2@app.acme-org.other-app.OrderService#1.0.0")));
    }

    @Test
    public void replyDestinationIsScopedToTheConnectionsReplyToId() {
        StompAuthorizer authorizer = organizationAuthorizer("acme-org");

        assertTrue(authorizer.subscribeAllowed(CRI.create("reply://" + REPLY_TO_ID + ":sub-1@kinoitc.js.EventBus/replyHandler")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("reply://other-id:sub-1@kinoitc.js.EventBus/replyHandler")));
    }

    @Test
    public void applicationIdsThatCannotFormAValidZoneAreRejected() {
        // an id containing a wildcard or dot could widen the connection's patterns, so creation must fail
        assertThrows(IllegalArgumentException.class, () -> applicationAuthorizer("acme-org", "orders.*"));
        assertThrows(IllegalArgumentException.class, () -> applicationAuthorizer("acme-org", "Orders-App"));
        assertThrows(IllegalArgumentException.class, () -> applicationAuthorizer("acme.org", "orders-app"));
    }

}
