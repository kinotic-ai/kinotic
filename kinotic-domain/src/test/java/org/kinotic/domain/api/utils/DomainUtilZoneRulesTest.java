package org.kinotic.domain.api.utils;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.ZonePartitioningService;
import org.kinotic.core.api.event.ZoneRules;
import org.kinotic.domain.api.model.security.participant.DefaultApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultSystemParticipant;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the zone routing rules {@link DomainUtil#zoneRules} derives per participant type: the verb x participant
 * x zone matrix, dot-boundary prefix safety, and rejection of ids that cannot form a valid zone, on a server that
 * serves every zone; and how a server's zone partitioning narrows them. The organization participant hosts the
 * services of {@link #HOSTED_ZONE}.
 */
public class DomainUtilZoneRulesTest {

    private static final String HOSTED_ZONE = "app.acme-org.orders-app";

    private ZonePartitioningService partitioning = ZonePartitioningService.everyZone("test");

    private ZoneRules applicationRules(String organizationId, String applicationId) {
        DefaultApplicationParticipant participant = DefaultApplicationParticipant.builder()
                                                                                 .id("app-user")
                                                                                 .organizationId(organizationId)
                                                                                 .applicationId(applicationId)
                                                                                 .metadata(Map.of())
                                                                                 .roles(List.of())
                                                                                 .build();
        return DomainUtil.zoneRules(participant, List.of()).restrictedTo(partitioning);
    }

    private ZoneRules organizationRules(String organizationId) {
        DefaultOrganizationParticipant participant = DefaultOrganizationParticipant.builder()
                                                                                   .id("org-user")
                                                                                   .organizationId(organizationId)
                                                                                   .metadata(Map.of())
                                                                                   .roles(List.of())
                                                                                   .build();
        return DomainUtil.zoneRules(participant, organizationId.equals("acme-org") ? List.of(HOSTED_ZONE) : List.of())
                         .restrictedTo(partitioning);
    }

    private ZoneRules systemRules() {
        DefaultSystemParticipant participant = DefaultSystemParticipant.builder()
                                                                       .id("system-user")
                                                                       .metadata(Map.of())
                                                                       .roles(List.of())
                                                                       .build();
        return DomainUtil.zoneRules(participant, List.of()).restrictedTo(partitioning);
    }

    @Test
    public void applicationParticipantSendRules() {
        ZoneRules rules = applicationRules("acme-org", "orders-app");

        // the app-api data plane and the app's own zone, including app declared sub zones
        assertTrue(rules.sendAllowed(CRI.create("srv://app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository/save#1.0.0")));
        assertTrue(rules.sendAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService/create#1.0.0")));
        assertTrue(rules.sendAllowed(CRI.create("srv://app.acme-org.orders-app.billing~InvoiceService/pay#1.0.0")));

        // the organization management surface, other applications, other organizations, the
        // system internals, and un-zoned addresses
        assertFalse(rules.sendAllowed(CRI.create("srv://management-api~org.kinotic.management.api.services.iam.MemberService/addOrganizationUser#1.0.0")));
        assertFalse(rules.sendAllowed(CRI.create("srv://app.acme-org.other-app~OrderService/create#1.0.0")));
        assertFalse(rules.sendAllowed(CRI.create("srv://app.other-org.orders-app~OrderService/create#1.0.0")));
        assertFalse(rules.sendAllowed(CRI.create("srv://system-api~org.kinotic.system.api.workload.WorkloadOrchestrationService/startWorkload#0.1.0")));
        assertFalse(rules.sendAllowed(CRI.create("srv://org.kinotic.management.api.services.iam.MemberService/findMembers#1.0.0")));
    }

    @Test
    public void legacyDottedZoneFormCarriesNoZoneAndIsDenied() {
        ZoneRules rules = applicationRules("acme-org", "orders-app");

        // the app's own zone written in the pre '~' dotted form is just an un-zoned resourceName
        assertFalse(rules.sendAllowed(CRI.create("srv://app.acme-org.orders-app.OrderService/create#1.0.0")));
        assertFalse(rules.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app.OrderService#1.0.0")));
        assertFalse(rules.sendAllowed(CRI.create("stream://app.acme-org.orders-app.OrderEvents")));
    }

    @Test
    public void slugifiedIdsWithUnderscoresFormValidZones() {
        assertTrue(applicationRules("acme-corp", "orders-app")
                           .sendAllowed(CRI.create("srv://app.acme-corp.orders-app~OrderService/create#1.0.0")));
        assertTrue(organizationRules("acme-corp")
                           .sendAllowed(CRI.create("srv://app.acme-corp.orders-app~OrderService/create#1.0.0")));
        assertFalse(applicationRules("acme-corp", "orders-app")
                            .sendAllowed(CRI.create("srv://app.acme-corp.other_app~OrderService/create#1.0.0")));
    }

    @Test
    public void zoneMatchingRespectsDotBoundaries() {
        // a zone that merely starts with the allowed zone text must not match
        ZoneRules application = applicationRules("acme-org", "orders-app");
        assertFalse(application.sendAllowed(CRI.create("srv://app.acme-org.orders-app-2~OrderService/create#1.0.0")));
        assertFalse(application.sendAllowed(CRI.create("srv://app.acme-org-2.orders-app~OrderService/create#1.0.0")));

        ZoneRules organization = organizationRules("acme-org");
        assertFalse(organization.subscribeAllowed(CRI.create("srv://app.acme-org-2.orders-app~OrderService#1.0.0")));
    }

    @Test
    public void applicationParticipantSubscribesInNoZone() {
        ZoneRules rules = applicationRules("acme-org", "orders-app");

        // not even its own zone: an application's services are hosted by its runtime, which
        // authenticates as an organization participant
        assertFalse(rules.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService#1.0.0")));
        assertFalse(rules.subscribeAllowed(CRI.create("stream://app.acme-org.orders-app~OrderEvents")));
        assertFalse(rules.subscribeAllowed(CRI.create("srv://app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository#1.0.0")));
        assertFalse(rules.subscribeAllowed(CRI.create("srv://management-api~org.kinotic.management.api.services.iam.MemberService#1.0.0")));
        assertFalse(rules.subscribeAllowed(CRI.create("srv://system-api~kinotic-ai.vm-manager.VmManager#0.1.0")));
    }

    @Test
    public void organizationParticipantSubscribesWithinTheZonesItHosts() {
        ZoneRules rules = organizationRules("acme-org");

        // the hosted application's zone, sub-zones included
        assertTrue(rules.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService#1.0.0")));
        assertTrue(rules.subscribeAllowed(CRI.create("srv://node1@app.acme-org.orders-app~OrderService#1.0.0")));
        assertTrue(rules.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app.billing~InvoiceService#1.0.0")));

        // never another application of the organization, though the participant may send to it
        assertFalse(rules.subscribeAllowed(CRI.create("srv://app.acme-org.other-app~OrderService#1.0.0")));
        assertTrue(rules.sendAllowed(CRI.create("srv://app.acme-org.other-app~OrderService/create#1.0.0")));
        assertFalse(organizationRules("other-org").subscribeAllowed(CRI.create("srv://app.other-org.orders-app~OrderService#1.0.0")));
        assertFalse(rules.subscribeAllowed(CRI.create("srv://app.other-org.orders-app~OrderService#1.0.0")));
        assertFalse(rules.subscribeAllowed(CRI.create("srv://app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository#1.0.0")));
        assertFalse(rules.subscribeAllowed(CRI.create("srv://management-api~org.kinotic.management.api.services.iam.MemberService#1.0.0")));
        assertFalse(rules.subscribeAllowed(CRI.create("srv://system-api~kinotic-ai.vm-manager.VmManager#0.1.0")));
    }

    @Test
    public void organizationParticipantSendRules() {
        ZoneRules rules = organizationRules("acme-org");

        // the management surface, the data plane, and its own applications' zones
        assertTrue(rules.sendAllowed(CRI.create("srv://management-api~org.kinotic.management.api.services.EntityDefinitionService/publish#1.0.0")));
        assertTrue(rules.sendAllowed(CRI.create("srv://management-api~org.kinotic.management.api.services.iam.MemberService/findMembers#1.0.0")));
        assertTrue(rules.sendAllowed(CRI.create("srv://app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository/save#1.0.0")));
        assertTrue(rules.sendAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService/create#1.0.0")));

        // never another organization's applications or the system internals
        assertFalse(rules.sendAllowed(CRI.create("srv://app.other-org.orders-app~OrderService/create#1.0.0")));
        assertFalse(rules.sendAllowed(CRI.create("srv://system-api~org.kinotic.system.api.services.workload.VmNodeOrchestrationService/query#1.0.0")));
    }

    @Test
    public void systemParticipantSendsAnywhereAndSubscribesToTheSystemZone() {
        ZoneRules rules = systemRules();

        assertTrue(rules.sendAllowed(CRI.create("srv://management-api~org.kinotic.management.api.services.iam.MemberService/findMembers#1.0.0")));
        assertTrue(rules.sendAllowed(CRI.create("srv://app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository/save#1.0.0")));
        assertTrue(rules.sendAllowed(CRI.create("srv://system-api~org.kinotic.system.api.workload.WorkloadOrchestrationService/startWorkload#0.1.0")));
        assertTrue(rules.sendAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService/create#1.0.0")));
        assertTrue(rules.sendAllowed(CRI.create("srv://node1@system-api~kinotic-ai.vm-manager.VmManager/startWorkload#0.1.0")));
        assertTrue(rules.sendAllowed(CRI.create("srv://org.kinotic.testserver.SomeLegacyService/method#1.0.0")));

        assertTrue(rules.subscribeAllowed(CRI.create("srv://system-api~kinotic-ai.vm-manager.VmManager#0.1.0")));
        assertTrue(rules.subscribeAllowed(CRI.create("srv://node1@system-api~kinotic-ai.vm-manager.VmManager#0.1.0")));

        // management-api and app-api are hosted in-process only, so not even system may subscribe to them
        assertFalse(rules.subscribeAllowed(CRI.create("srv://management-api~org.kinotic.some.PlatformService#1.0.0")));
        assertFalse(rules.subscribeAllowed(CRI.create("srv://app-api~org.kinotic.some.DataService#1.0.0")));
        assertFalse(rules.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService#1.0.0")));
        // subscribing requires a zone: an un-zoned address is never subscribable, even for system
        assertFalse(rules.subscribeAllowed(CRI.create("srv://org.kinotic.testserver.SomeLegacyService#1.0.0")));
    }

    @Test
    public void dottedScopesAreAuthorizedByTheirZone() {
        // a node id may be an FQDN; the zone comes from the CRI's zone component so the scope
        // cannot influence the decision
        ZoneRules system = systemRules();
        assertTrue(system.subscribeAllowed(CRI.create("srv://vm1.example.com@system-api~kinotic-ai.vm-manager.VmManager#0.1.0")));
        assertTrue(system.sendAllowed(CRI.create("srv://vm1.example.com@system-api~kinotic-ai.vm-manager.VmManager/start#0.1.0")));

        ZoneRules organization = organizationRules("acme-org");
        assertTrue(organization.subscribeAllowed(CRI.create("srv://node.1.2@app.acme-org.orders-app~OrderService#1.0.0")));
        assertFalse(organization.subscribeAllowed(CRI.create("srv://node.1.2@app.other-org.orders-app~OrderService#1.0.0")));
    }

    @Test
    public void theManagementServerRoutesOnlyToZonesItReaches() {
        partitioning = ZonePartitioningService.of("management",
                                                  Set.of("management-api"),
                                                  Set.of("management-api", "system-api", "app-api"));
        ZoneRules organization = organizationRules("acme-org");

        assertTrue(organization.sendAllowed(CRI.create("srv://management-api~org.kinotic.management.api.services.iam.MemberService/findMembers#1.0.0")));
        assertTrue(organization.sendAllowed(CRI.create("srv://app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository/save#1.0.0")));
        // the participant may address its applications' zones, but the management server does not reach them
        assertFalse(organization.sendAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService/create#1.0.0")));
        // nor host them, so an application's runtime cannot publish its services here
        assertFalse(organization.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService#1.0.0")));
    }

    @Test
    public void theAppServerHostsApplicationZonesOnly() {
        partitioning = ZonePartitioningService.of("app", Set.of("app-api", "app"), Set.of("app-api", "app"));
        ZoneRules organization = organizationRules("acme-org");

        assertTrue(organization.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService#1.0.0")));
        assertTrue(organization.sendAllowed(CRI.create("srv://app.acme-org.orders-app~OrderService/create#1.0.0")));
        assertFalse(organization.sendAllowed(CRI.create("srv://management-api~org.kinotic.management.api.services.iam.MemberService/findMembers#1.0.0")));

        // system participants may send anywhere, but only where this server reaches
        ZoneRules system = systemRules();
        assertFalse(system.sendAllowed(CRI.create("srv://system-api~org.kinotic.system.api.services.workload.VmNodeOrchestrationService/query#1.0.0")));
        assertFalse(system.subscribeAllowed(CRI.create("srv://system-api~kinotic-ai.vm-manager.VmManager#0.1.0")));
    }

    @Test
    public void craftedScopeEmbeddingTheOwnZoneCannotReachAnotherApp() {
        ZoneRules rules = applicationRules("acme-org", "orders-app");

        // The scope precedes the resourceName in the raw CRI, so an attacker could try to prefix
        // the raw string with its own allowed zone and target another app after the '@'. Zone
        // authorization must run against the resource the message actually routes to, not the scope.
        assertFalse(rules.sendAllowed(CRI.create("srv://app.acme-org.orders-app.x@app.acme-org.other-app~OrderService/create#1.0.0")));
        // a scope carrying the zone delimiter cannot even be constructed (see CRITests)
        assertThrows(IllegalArgumentException.class,
                     () -> CRI.create("srv://app.acme-org.orders-app~x@app.acme-org.other-app~OrderService/create#1.0.0"));
        assertFalse(rules.sendAllowed(CRI.create("srv://app.acme-org.orders-app@management-api~org.kinotic.management.api.services.iam.MemberService/addOrganizationUser#1.0.0")));
        assertFalse(rules.subscribeAllowed(CRI.create("srv://app.acme-org.orders-app.x@app.acme-org.other-app~OrderService#1.0.0")));
        assertFalse(rules.sendAllowed(CRI.create("stream://app.acme-org.orders-app.x@app.acme-org.other-app~OrderEvents")));
    }

    @Test
    public void streamSchemeIsNotRoutable() {
        // stream routing is not yet supported by the gateway, so every participant is denied
        assertFalse(systemRules().sendAllowed(CRI.create("stream://app.acme-org.orders-app~OrderEvents")));
        assertFalse(organizationRules("acme-org").sendAllowed(CRI.create("stream://app.acme-org.orders-app~OrderEvents")));
        assertFalse(organizationRules("acme-org").subscribeAllowed(CRI.create("stream://app.acme-org.orders-app~OrderEvents")));
        assertFalse(applicationRules("acme-org", "orders-app").sendAllowed(CRI.create("stream://app.acme-org.orders-app~OrderEvents")));
    }

    @Test
    public void aHostedZoneOutsideTheOrganizationIsRejected() {
        DefaultOrganizationParticipant participant = DefaultOrganizationParticipant.builder()
                                                                                   .id("org-user")
                                                                                   .organizationId("acme-org")
                                                                                   .metadata(Map.of())
                                                                                   .roles(List.of())
                                                                                   .build();

        assertThrows(IllegalArgumentException.class, () -> DomainUtil.zoneRules(participant, List.of("app.other-org.orders-app")));
        // a zone that merely begins with the organization's id is another organization's
        assertThrows(IllegalArgumentException.class, () -> DomainUtil.zoneRules(participant, List.of("app.acme-org-2.orders-app")));
        assertThrows(IllegalArgumentException.class, () -> DomainUtil.zoneRules(participant, List.of("management-api")));
    }

    @Test
    public void applicationIdsThatCannotFormAValidZoneAreRejected() {
        // an id containing a dot or uppercase could shift the zone's label structure, so creation must fail
        assertThrows(IllegalArgumentException.class, () -> applicationRules("acme-org", "orders.*"));
        assertThrows(IllegalArgumentException.class, () -> applicationRules("acme-org", "Orders-App"));
        assertThrows(IllegalArgumentException.class, () -> applicationRules("acme.org", "orders-app"));
    }

}
