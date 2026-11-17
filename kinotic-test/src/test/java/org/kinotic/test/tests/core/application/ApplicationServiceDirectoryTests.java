package org.kinotic.test.tests.core.application;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.app.api.services.ApplicationServiceDirectory;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.McpToolC3Decorator;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how an application runtime registers its services in the directory: an organization registers a
 * service of one of its applications in that application's zone, a service published in no namespace is addressed
 * in the zone's namespace, a function declared a tool is stored as one, and a registration in another zone or for an application the organization does not
 * own is refused.
 */
@SpringBootTest
public class ApplicationServiceDirectoryTests extends KinoticTestBase {

    private static final String NAMESPACE = "com.acme.reports";

    @Autowired
    private ApplicationServiceDirectory applicationServiceDirectory;

    @Autowired
    private ServiceDirectory serviceDirectory;

    @Autowired
    private ApplicationService applicationService;

    @Test
    public void anOrganizationRegistersAServiceOfItsApplication() throws Exception {
        String appId = application();
        String zone = DomainUtil.applicationZone(TEST_ORG_ID, appId);
        ServiceDirectoryEntry entry = entry(appId, zone, definition(NAMESPACE, "ReportService"));
        // the organization is the caller's, whatever the entry names
        entry.setOrganizationId("another-org");

        await(runAsOrganization(() -> applicationServiceDirectory.register(entry)));

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
        await(runAsOrganization(() -> applicationServiceDirectory.register(entry(appId, zone, definition(NAMESPACE, "ReportService")))));
        assertEquals(1, entries(appId).size());
    }

    @Test
    public void aServiceInNoNamespaceIsAddressedInTheZonesNamespace() throws Exception {
        String appId = application();
        String zone = DomainUtil.applicationZone(TEST_ORG_ID, appId) + ".probes";

        await(runAsOrganization(() -> applicationServiceDirectory.register(entry(appId, zone, definition(null, "Probe")))));

        ServiceDirectoryEntry stored = stored(appId, zone + "~Probe");
        assertEquals(zone, stored.getNamespace());
        assertEquals("srv://" + zone + "~Probe", stored.getServiceAddress());
    }

    @Test
    public void aRegistrationOutsideTheApplicationIsRefused() throws Exception {
        String appId = application();
        String otherZone = DomainUtil.applicationZone(TEST_ORG_ID, "other-" + appId);
        String unknownApp = "unknown-app-" + suffix();

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
    public void aFunctionDeclaredAToolIsStoredAsOneOfTheServicesTools() throws Exception {
        String appId = application();
        String zone = DomainUtil.applicationZone(TEST_ORG_ID, appId);
        ServiceDefinition definition = definition(NAMESPACE, "ReportService");
        definition.getFunctions().iterator().next().setDecorators(List.of(new McpToolC3Decorator().setTitle("Reports Find Reports")
                                                                                            .setDescription("Finds every report")
                                                                                            .setReadOnlyHint(true)));

        await(runAsOrganization(() -> applicationServiceDirectory.register(entry(appId, zone, definition))));

        ServiceDirectoryEntry stored = stored(appId, zone + "~" + NAMESPACE + ".ReportService");
        assertTrue(stored.isMcpExposed());
        assertEquals(1, stored.getMcpTools().size());
        McpToolDefinition tool = stored.getMcpTools().getFirst();
        assertEquals(KinoticUtil.mcpToolName(zone + "~" + NAMESPACE + ".ReportService", "findReports"), tool.getName());
        assertEquals("Reports Find Reports", tool.getTitle());
        assertEquals("Reports Find Reports: Finds every report", tool.getDescription());
        assertEquals("srv://" + zone + "~" + NAMESPACE + ".ReportService/findReports", tool.getCri());
        assertTrue(tool.getAnnotations().isReadOnlyHint());
        assertFalse(tool.getAnnotations().isDestructiveHint());
    }

    @Test
    public void aToolStatingNoDescriptionIsRefused() throws Exception {
        String appId = application();
        ServiceDefinition definition = definition(NAMESPACE, "ReportService");
        definition.getFunctions().iterator().next().setDecorators(List.of(new McpToolC3Decorator().setTitle("Reports Find Reports")));

        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> applicationServiceDirectory.register(entry(appId, DomainUtil.applicationZone(TEST_ORG_ID, appId), definition))));
        assertTrue(entries(appId).isEmpty());
    }

    private String application() throws Exception {
        String ret = "directory-app-" + suffix();
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(ret, "Directory application", null)));
        return ret;
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
        return assertThrows(ExecutionException.class, () -> await(runAsOrganization(call::get))).getCause();
    }
}
