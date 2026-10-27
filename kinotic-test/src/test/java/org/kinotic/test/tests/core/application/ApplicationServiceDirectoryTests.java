package org.kinotic.test.tests.core.application;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.app.api.services.ApplicationServiceDirectory;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.ZoneRules;
import org.kinotic.core.api.event.ZoneRulesResolver;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how an application runtime registers and hosts its services: a runtime registers a service of the
 * application it is granted the runtime of in that application's zone, a service published in no namespace is
 * addressed in the zone's namespace, and a registration in another zone, for an application the organization does
 * not own is refused. The gateway admits a registration only from a runtime granted the application's runtime role.
 * A runtime subscribes only in the zones of the applications it is granted. Each test writes the grant a
 * deployment writes for its runtime machine.
 */
@SpringBootTest
public class ApplicationServiceDirectoryTests extends KinoticTestBase {

    private static final String NAMESPACE = "com.acme.reports";
    private static final String DIRECTORY_SERVICE = DomainUtil.APP_API_ZONE + "~" + ApplicationServiceDirectory.class.getName();

    @Autowired
    private ApplicationServiceDirectory applicationServiceDirectory;

    @Autowired
    private ServiceDirectory serviceDirectory;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private RelationshipService relationships;

    @Autowired
    private ZoneRulesResolver zoneRulesResolver;

    private final OrganizationParticipant runtime = new DefaultOrganizationParticipant(UUID.randomUUID().toString(),
                                                                                      TEST_ORG_ID,
                                                                                      Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                                             ParticipantConstants.PARTICIPANT_TYPE_MACHINE),
                                                                                      List.of());

    @Test
    public void aRuntimeRegistersAServiceOfItsApplication() throws Exception {
        String appId = grantedApplication();
        String zone = DomainUtil.applicationZone(TEST_ORG_ID, appId);
        ServiceDirectoryEntry entry = entry(appId, zone, definition(NAMESPACE, "ReportService"));
        // the organization is the caller's, whatever the entry names
        entry.setOrganizationId("another-org");

        await(runAs(runtime, () -> applicationServiceDirectory.register(entry)));

        ServiceDirectoryEntry stored = stored(appId, zone + "~" + NAMESPACE + ".ReportService");
        assertEquals(TEST_ORG_ID, stored.getOrganizationId());
        assertEquals(appId, stored.getApplicationId());
        assertEquals(zone, stored.getZone());
        assertEquals("1.0.0", stored.getVersion());
        assertEquals("srv://" + zone + "~" + NAMESPACE + ".ReportService", stored.getServiceAddress());
        assertEquals(List.of("findReports", "generate"),
                     stored.getServiceDefinition().getFunctions().stream().map(FunctionDefinition::getName).toList());
        // no runtime listens at the address
        assertFalse(stored.isOnline());

        // registering the same contract again leaves the one entry
        await(runAs(runtime, () -> applicationServiceDirectory.register(entry(appId, zone, definition(NAMESPACE, "ReportService")))));
        assertEquals(1, entries(appId).size());
    }

    @Test
    public void aServiceInNoNamespaceIsAddressedInTheZonesNamespace() throws Exception {
        String appId = grantedApplication();
        String zone = DomainUtil.applicationZone(TEST_ORG_ID, appId) + ".probes";

        await(runAs(runtime, () -> applicationServiceDirectory.register(entry(appId, zone, definition(null, "Probe")))));

        ServiceDirectoryEntry stored = stored(appId, zone + "~Probe");
        assertEquals(zone, stored.getNamespace());
        assertEquals("srv://" + zone + "~Probe", stored.getServiceAddress());
    }

    @Test
    public void aRegistrationOutsideTheApplicationIsRefused() throws Exception {
        String appId = grantedApplication();
        String otherZone = DomainUtil.applicationZone(TEST_ORG_ID, "other-" + appId);
        // granted, as a runtime of an application since deleted is
        String unknownApp = "unknown-app-" + suffix();
        grantRuntime(unknownApp);

        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> applicationServiceDirectory.register(entry(appId, otherZone, definition(NAMESPACE, "ReportService")))));
        // a zone that merely begins with the application's name is another application's
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> applicationServiceDirectory.register(entry(appId, DomainUtil.applicationZone(TEST_ORG_ID, appId) + "x",
                                                                              definition(NAMESPACE, "ReportService")))));
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> applicationServiceDirectory.register(entry(unknownApp, DomainUtil.applicationZone(TEST_ORG_ID, unknownApp),
                                                                              definition(NAMESPACE, "ReportService")))));
        assertTrue(entries(appId).isEmpty());
        assertTrue(entries(unknownApp).isEmpty());
    }

    @Test
    public void onlyARuntimeGrantedTheApplicationRegistersItsServices() throws Exception {
        String appId = application();
        List<ServiceDirectoryEntry> register = List.of(entry(appId, DomainUtil.applicationZone(TEST_ORG_ID, appId),
                                                             definition(NAMESPACE, "ReportService")));

        assertRefused(DIRECTORY_SERVICE, "register", runtime, register, AuthzConstants.CAN_REGISTER_SERVICES);
        grantRuntime(appId);
        assertTrue(awaitUntil(() -> admitted(DIRECTORY_SERVICE, "register", runtime, register)), "the granted runtime was never admitted");
        // a person of the organization holding no role registers no application's services
        OrganizationParticipant person = new DefaultOrganizationParticipant(UUID.randomUUID().toString(),
                                                                            TEST_ORG_ID,
                                                                            Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                                   ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                                            List.of());
        assertRefused(DIRECTORY_SERVICE, "register", person, register, AuthzConstants.CAN_REGISTER_SERVICES);
    }

    @Test
    public void aRuntimeSubscribesOnlyInTheZonesOfTheApplicationsItIsGranted() throws Exception {
        String appId = grantedApplication();
        String otherAppId = application();

        ZoneRules rules = await(zoneRulesResolver.resolve(runtime));

        assertTrue(rules.subscribeAllowed(CRI.create("srv://" + DomainUtil.applicationZone(TEST_ORG_ID, appId) + "~ReportService#1.0.0")));
        assertTrue(rules.subscribeAllowed(CRI.create("srv://" + DomainUtil.applicationZone(TEST_ORG_ID, appId) + ".probes~Probe#1.0.0")));
        assertFalse(rules.subscribeAllowed(CRI.create("srv://" + DomainUtil.applicationZone(TEST_ORG_ID, otherAppId) + "~ReportService#1.0.0")));
        // it calls the services of the organization's other applications
        assertTrue(rules.sendAllowed(CRI.create("srv://" + DomainUtil.applicationZone(TEST_ORG_ID, otherAppId) + "~ReportService/generate#1.0.0")));
        // a person hosts no application's services
        assertFalse(await(zoneRulesResolver.resolve(TEST_ORGANIZATION_PARTICIPANT))
                            .subscribeAllowed(CRI.create("srv://" + DomainUtil.applicationZone(TEST_ORG_ID, appId) + "~ReportService#1.0.0")));
    }

    private String application() throws Exception {
        String ret = "directory-app-" + suffix();
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(ret, "Directory application", null)));
        return ret;
    }

    // an application with this test's runtime granted its runtime, as deploying one of its projects grants
    private String grantedApplication() throws Exception {
        String ret = application();
        grantRuntime(ret);
        return ret;
    }

    private void grantRuntime(String applicationId) throws Exception {
        await(relationships.ensureBound(AuthzStoreService.SYSTEM,
                                        "runtime-of-" + runtime.getId() + "-" + applicationId,
                                        AuthzConstants.APPLICATION_RUNTIME_ROLE,
                                        AuthzUtil.object(AuthzConstants.USER_TYPE, runtime.getId()),
                                        AuthzGraphUtil.authzApplication(TEST_ORG_ID, applicationId)));
    }

    private static ServiceDirectoryEntry entry(String applicationId, String zone, ServiceDefinition definition) {
        return new ServiceDirectoryEntry().setApplicationId(applicationId)
                                          .setZone(zone)
                                          .setVersion("1.0.0")
                                          .setServiceDefinition(definition);
    }

    private static ServiceDefinition definition(String namespace, String name) {
        return new ServiceDefinition().setNamespace(namespace)
                                      .setName(name)
                                      .addFunction(new FunctionDefinition().setName("findReports"))
                                      .addFunction(new FunctionDefinition().setName("generate"));
    }

    private ServiceDirectoryEntry stored(String applicationId, String id) throws Exception {
        List<ServiceDirectoryEntry> entries = entries(applicationId);
        return entries.stream()
                      .filter(entry -> id.equals(entry.getId()))
                      .findFirst()
                      .orElseThrow(() -> new AssertionError("no directory entry " + id + " in "
                                                                    + entries.stream().map(ServiceDirectoryEntry::getId).toList()));
    }

    private List<ServiceDirectoryEntry> entries(String applicationId) throws Exception {
        return serviceDirectory.findEntriesScopedTo(TEST_ORG_ID, applicationId, Pageable.create(0, 100, Sort.by("id")))
                               .await()
                               .getContent();
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return failure(runtime, call);
    }

    private Throwable failure(OrganizationParticipant participant, Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAs(participant, call::get))).getCause();
    }
}
