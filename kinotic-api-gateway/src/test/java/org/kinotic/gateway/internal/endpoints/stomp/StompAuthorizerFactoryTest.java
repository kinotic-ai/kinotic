package org.kinotic.gateway.internal.endpoints.stomp;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.domain.api.model.security.DefaultApplicationParticipant;
import org.kinotic.domain.api.model.security.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.DefaultSystemParticipant;

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

        // the app-api data plane and the app's own zone, including app declared sub zones
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository/save#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService/create#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app.billing~InvoiceService/pay#1.0.0")));

        // the organization management surface, other applications, other organizations, the
        // platform internals, and un-zoned addresses
        assertFalse(authorizer.sendAllowed(CRI.create("srv://management-api~org.kinotic.os.api.services.iam.MemberService/inviteMember#1.0.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.acme-org.other-app~OrderService/create#1.0.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.other-org.orders-app~OrderService/create#1.0.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("srv://system-api~org.kinotic.system.api.workload.WorkloadOrchestrationService/startWorkload#0.1.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("srv://org.kinotic.os.api.services.iam.MemberService/findMembers#1.0.0")));
    }

    @Test
    public void legacyDottedZoneFormCarriesNoZoneAndIsDenied() {
        StompAuthorizer authorizer = applicationAuthorizer("acme-org", "orders-app");

        // the app's own zone written in the pre '~' dotted form is just an un-zoned resourceName
        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app.OrderService/create#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app.OrderService#1.0.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("stream://app.acme-org.orders-app.OrderEvents")));
    }

    @Test
    public void slugifiedIdsWithUnderscoresFormValidZones() {
        assertTrue(applicationAuthorizer("acme-corp", "orders-app")
                           .sendAllowed(CRI.create("srv://app.acme-corp.orders-app~OrderService/create#1.0.0")));
        assertTrue(organizationAuthorizer("acme-corp")
                           .subscribeAllowed(CRI.create("srv://app.acme-corp.orders-app~OrderService#1.0.0")));
        assertFalse(applicationAuthorizer("acme-corp", "orders-app")
                            .sendAllowed(CRI.create("srv://app.acme-corp.other_app~OrderService/create#1.0.0")));
    }

    @Test
    public void zoneMatchingRespectsDotBoundaries() {
        // a zone that merely starts with the allowed zone text must not match
        StompAuthorizer application = applicationAuthorizer("acme-org", "orders-app");
        assertFalse(application.sendAllowed(CRI.create("srv://app.acme-org.orders-app-2~OrderService/create#1.0.0")));
        assertFalse(application.sendAllowed(CRI.create("srv://app.acme-org-2.orders-app~OrderService/create#1.0.0")));

        StompAuthorizer organization = organizationAuthorizer("acme-org");
        assertFalse(organization.subscribeAllowed(CRI.create("srv://app.acme-org-2.orders-app~OrderService#1.0.0")));
    }

    @Test
    public void applicationParticipantSubscribesOnlyToReplyDestinations() {
        StompAuthorizer authorizer = applicationAuthorizer("acme-org", "orders-app");

        assertTrue(authorizer.subscribeAllowed(CRI.create("reply://" + REPLY_TO_ID + ":sub-1@kinotic.js.EventBus/replyHandler")));

        // not even its own zone: an application's services are hosted by its runtime, which
        // authenticates as an organization participant
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("stream://app.acme-org.orders-app~OrderEvents")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://management-api~org.kinotic.os.api.services.iam.MemberService#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://system-api~kinotic-ai.vm-manager.VmManager#0.1.0")));
    }

    @Test
    public void organizationParticipantSubscribesWithinItsOwnAppZones() {
        StompAuthorizer authorizer = organizationAuthorizer("acme-org");

        // every application zone under the organization, sub-zones included, srv and stream
        assertTrue(authorizer.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService#1.0.0")));
        assertTrue(authorizer.subscribeAllowed(CRI.create("srv://node1@app.acme-org.orders-app~OrderService#1.0.0")));
        assertTrue(authorizer.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app.billing~InvoiceService#1.0.0")));

        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app.other-org.orders-app~OrderService#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://management-api~org.kinotic.os.api.services.iam.MemberService#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://system-api~kinotic-ai.vm-manager.VmManager#0.1.0")));
    }

    @Test
    public void organizationParticipantSendRules() {
        StompAuthorizer authorizer = organizationAuthorizer("acme-org");

        // the management surface, the data plane, and its own applications' zones
        assertTrue(authorizer.sendAllowed(CRI.create("srv://management-api~org.kinotic.persistence.api.services.EntityDefinitionService/publish#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://management-api~org.kinotic.os.api.services.iam.MemberService/findMembers#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository/save#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService/create#1.0.0")));

        // never another organization's applications or the platform internals
        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.other-org.orders-app~OrderService/create#1.0.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("srv://system-api~org.kinotic.os.api.services.LogManager/query#1.0.0")));
    }

    @Test
    public void systemParticipantSendsAnywhereAndSubscribesToTheSystemZone() {
        StompAuthorizer authorizer = systemAuthorizer();

        assertTrue(authorizer.sendAllowed(CRI.create("srv://management-api~org.kinotic.os.api.services.iam.MemberService/findMembers#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository/save#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://system-api~org.kinotic.system.api.workload.WorkloadOrchestrationService/startWorkload#0.1.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService/create#1.0.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://node1@system-api~kinotic-ai.vm-manager.VmManager/startWorkload#0.1.0")));
        assertTrue(authorizer.sendAllowed(CRI.create("srv://org.kinotic.server.clienttest.SomeLegacyService/method#1.0.0")));

        assertTrue(authorizer.subscribeAllowed(CRI.create("srv://system-api~kinotic-ai.vm-manager.VmManager#0.1.0")));
        assertTrue(authorizer.subscribeAllowed(CRI.create("srv://node1@system-api~kinotic-ai.vm-manager.VmManager#0.1.0")));

        // os-api and app-api are hosted in-process only, so not even system may subscribe to them
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://management-api~org.kinotic.some.PlatformService#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app-api~org.kinotic.some.DataService#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService#1.0.0")));
        // subscribing requires a zone: an un-zoned address is never subscribable, even for system
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://org.kinotic.server.clienttest.SomeLegacyService#1.0.0")));
    }

    @Test
    public void dottedScopesAreAuthorizedByTheirZone() {
        // a node id may be an FQDN; the zone comes from the CRI's zone component so the scope
        // cannot influence the decision
        StompAuthorizer system = systemAuthorizer();
        assertTrue(system.subscribeAllowed(CRI.create("srv://vm1.example.com@system-api~kinotic-ai.vm-manager.VmManager#0.1.0")));
        assertTrue(system.sendAllowed(CRI.create("srv://vm1.example.com@system-api~kinotic-ai.vm-manager.VmManager/start#0.1.0")));

        StompAuthorizer organization = organizationAuthorizer("acme-org");
        assertTrue(organization.subscribeAllowed(CRI.create("srv://node.1.2@app.acme-org.orders-app~OrderService#1.0.0")));
        assertFalse(organization.subscribeAllowed(CRI.create("srv://node.1.2@app.other-org.orders-app~OrderService#1.0.0")));
    }

    @Test
    public void craftedScopeEmbeddingTheOwnZoneCannotReachAnotherApp() {
        StompAuthorizer authorizer = applicationAuthorizer("acme-org", "orders-app");

        // The scope precedes the resourceName in the raw CRI, so an attacker could try to prefix
        // the raw string with its own allowed zone and target another app after the '@'. Zone
        // authorization must run against the resource the message actually routes to, not the scope.
        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app.x@app.acme-org.other-app~OrderService/create#1.0.0")));
        // a scope carrying the zone delimiter cannot even be constructed (see CRITests)
        assertThrows(IllegalArgumentException.class,
                     () -> CRI.create("srv://app.acme-org.orders-app~x@app.acme-org.other-app~OrderService/create#1.0.0"));
        assertFalse(authorizer.sendAllowed(CRI.create("srv://app.acme-org.orders-app@os-api~org.kinotic.os.api.services.iam.MemberService/inviteMember#1.0.0")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app.x@app.acme-org.other-app~OrderService#1.0.0")));
        assertFalse(authorizer.sendAllowed(CRI.create("stream://app.acme-org.orders-app.x@app.acme-org.other-app~OrderEvents")));
    }

    @Test
    public void streamSchemeIsNotRoutable() {
        // stream routing is not yet supported by the gateway, so every participant is denied
        assertFalse(systemAuthorizer().sendAllowed(CRI.create("stream://app.acme-org.orders-app~OrderEvents")));
        assertFalse(organizationAuthorizer("acme-org").sendAllowed(CRI.create("stream://app.acme-org.orders-app~OrderEvents")));
        assertFalse(organizationAuthorizer("acme-org").subscribeAllowed(CRI.create("stream://app.acme-org.orders-app~OrderEvents")));
        assertFalse(applicationAuthorizer("acme-org", "orders-app").sendAllowed(CRI.create("stream://app.acme-org.orders-app~OrderEvents")));
    }

    @Test
    public void replyDestinationIsScopedToTheConnectionsReplyToId() {
        StompAuthorizer authorizer = organizationAuthorizer("acme-org");

        assertTrue(authorizer.subscribeAllowed(CRI.create("reply://" + REPLY_TO_ID + ":sub-1@kinotic.js.EventBus/replyHandler")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("reply://other-id:sub-1@kinotic.js.EventBus/replyHandler")));
        assertFalse(authorizer.subscribeAllowed(CRI.create("reply://kinotic.js.EventBus/replyHandler")));
    }

    @Test
    public void temporaryGrantsAllowOneExactMatchSend() {
        StompAuthorizer authorizer = organizationAuthorizer("acme-org");
        String replyDestination = "reply://" + REPLY_TO_ID + ":sub-1@kinotic.js.EventBus/replyHandler";

        assertFalse(authorizer.sendAllowed(CRI.create(replyDestination)));

        // grants match by exact string, so a case variant of the granted destination stays denied
        authorizer.addTemporarySendAllowed(replyDestination);
        assertFalse(authorizer.sendAllowed(CRI.create("reply://" + REPLY_TO_ID + ":sub-1@Kinotic.js.EventBus/replyHandler")));
        assertTrue(authorizer.sendAllowed(CRI.create(replyDestination)));

        // a grant authorizes exactly one send
        assertFalse(authorizer.sendAllowed(CRI.create(replyDestination)));
    }

    @Test
    public void applicationIdsThatCannotFormAValidZoneAreRejected() {
        // an id containing a dot or uppercase could shift the zone's label structure, so creation must fail
        assertThrows(IllegalArgumentException.class, () -> applicationAuthorizer("acme-org", "orders.*"));
        assertThrows(IllegalArgumentException.class, () -> applicationAuthorizer("acme-org", "Orders-App"));
        assertThrows(IllegalArgumentException.class, () -> applicationAuthorizer("acme.org", "orders-app"));
    }

}
