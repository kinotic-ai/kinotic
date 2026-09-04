package org.kinotic.system.internal.api.services;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.cdn.CdnManager;
import com.azure.resourcemanager.cdn.fluent.CdnManagementClient;
import com.azure.resourcemanager.cdn.fluent.models.AfdDomainInner;
import com.azure.resourcemanager.cdn.fluent.models.AfdEndpointInner;
import com.azure.resourcemanager.cdn.fluent.models.RouteInner;
import com.azure.resourcemanager.cdn.fluent.models.RuleInner;
import com.azure.resourcemanager.cdn.models.UrlRewriteAction;
import com.azure.resourcemanager.dns.DnsZoneManager;
import com.azure.resourcemanager.dns.models.CnameRecordSet;
import com.azure.resourcemanager.dns.models.DnsZone;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.BlobContainer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.kinotic.domain.api.model.DeploymentStatus;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationStorage;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.config.OrganizationStorageProperties;
import org.kinotic.system.api.config.UiDeploymentProperties;
import org.kinotic.system.api.services.OrganizationStorageProvisioner;
import org.kinotic.system.api.services.UiStoragePaths;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Provisions one organization and one site against a real Azure subscription, the way the
 * provisioning job and a deployment do, and reads back what Azure holds. Runs only on a
 * developer machine set up as the contributing guide describes: the {@code local} profile in
 * {@code kinotic-server/src/main/resources/application-local.yml} names the subscription,
 * resource group, Front Door profile and DNS zone, and the service principal's
 * {@code AZURE_*} variables are in the environment, which the module's test task takes from
 * {@code .env.local}. Skipped everywhere else. Everything it creates is idempotent and left in
 * place, so a second run reads through what the first created.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AzureProvisioningIntegrationTest {

    private static final Path LOCAL_PROFILE = Path.of("..", "kinotic-server", "src", "main", "resources", "application-local.yml");
    private static final String ORGANIZATION_ID = "kinotic-azure-it";
    private static final String APPLICATION_ID = "azure-it-app";
    private static final String PROJECT_ID = "azure-it-project";
    private static final String UI_NAME = "web";
    private static final String SITE_LABEL = "azure-it";
    /** A storage account or a Front Door write takes minutes; a step past this is stuck. */
    private static final long STEP_TIMEOUT_MINUTES = 15;

    private Vertx vertx;
    private KinoticSystemApiProperties properties;
    private TokenCredential credential;
    private AzureOrganizationStorageProvisioner storageProvisioner;
    private FrontDoorUiDeploymentProvisioner siteProvisioner;
    private Organization organization;

    @BeforeAll
    void requireAzure() throws IOException {
        assumeTrue(Files.exists(LOCAL_PROFILE), "no " + LOCAL_PROFILE + ": not a developer machine set up for Azure");
        assumeTrue(System.getenv("AZURE_CLIENT_ID") != null, "AZURE_CLIENT_ID is not set: .env.local is not in the environment");
        properties = load(LOCAL_PROFILE);
        assumeFalse(storageProperties().isDisableProvisioner(), "the local profile disables the storage provisioner");
        assumeFalse(uiProperties().isDisableProvisioner(), "the local profile disables the site provisioner");

        vertx = Vertx.vertx();
        credential = new DefaultAzureCredentialBuilder().build();
        StubOrganizationService organizations = new StubOrganizationService();
        organizations.saved.put(ORGANIZATION_ID, new Organization().setId(ORGANIZATION_ID)
                                                                   .setName("Azure integration test")
                                                                   .setCreated(new Date()));
        storageProvisioner = new AzureOrganizationStorageProvisioner(organizations, vertx, properties);
        siteProvisioner = new FrontDoorUiDeploymentProvisioner(vertx, properties,
                                                               new AzureOrganizationStorageService(vertx, properties),
                                                               new StubUiDeploymentRepository());
    }

    @AfterAll
    void close() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    @Order(1)
    @Timeout(value = STEP_TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void provisionsTheStorageAccount() {
        organization = await(storageProvisioner.ensureStorage(ORGANIZATION_ID));

        OrganizationStorage storage = organization.getStorage();
        assertEquals(DeploymentStatusType.READY, storage.getStatus().type(), storage.getStatus().message());
        assertTrue(storage.getAzureBlobEndpoint().startsWith("https://"), storage.getAzureBlobEndpoint());

        StorageManager storageManager = StorageManager.authenticate(credential, profileOf(storage.getAzureSubscriptionId()));
        BlobContainer container = storageManager.blobContainers().get(storageProperties().getResourceGroup(),
                                                                      storage.getAzureAccountName(),
                                                                      OrganizationStorageProvisioner.UI_CONTAINER);
        assertNotNull(container, "the " + OrganizationStorageProvisioner.UI_CONTAINER + " container exists");
    }

    @Test
    @Order(2)
    @Timeout(value = STEP_TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void preparesFrontDoorForTheOrganization() {
        assumeTrue(organization != null, "the storage step did not complete");

        await(siteProvisioner.prepareOrganization(organization));

        CdnManagementClient cdn = cdnClient();
        assertNotNull(cdn.getAfdOriginGroups().get(resourceGroup(), profileName(), "org-" + ORGANIZATION_ID),
                      "the organization's origin group exists");
        String ruleSet = "org" + ORGANIZATION_ID.replace("-", "");
        for (String name : List.of("asset", "spa")) {
            RuleInner rule = cdn.getRules().get(resourceGroup(), profileName(), ruleSet, name);
            UrlRewriteAction rewrite = (UrlRewriteAction) rule.actions().getFirst();
            String destination = rewrite.parameters().destination();
            assertTrue(destination.startsWith("/"), name + " rule rewrites to " + destination);
            assertTrue(destination.contains("sig="), name + " rule carries the SAS: " + destination);
        }
    }

    @Test
    @Order(3)
    @Timeout(value = STEP_TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void provisionsASite() {
        assumeTrue(organization != null, "the storage step did not complete");
        UiDeployment deployment = new UiDeployment().setId(SITE_LABEL)
                                                    .setOrganizationId(ORGANIZATION_ID)
                                                    .setApplicationId(APPLICATION_ID)
                                                    .setProjectId(PROJECT_ID)
                                                    .setName(UI_NAME)
                                                    .setCommitSha("0000000000000000000000000000000000000000")
                                                    .setStatus(new DeploymentStatus(DeploymentStatusType.PROVISIONING))
                                                    .setCreated(new Date())
                                                    .setUpdated(new Date());

        UiDeployment provisioned = await(siteProvisioner.provision(deployment, organization));

        // ready only once Front Door has validated the hostname, which takes minutes; provisioning is the expected first answer
        assertNotEquals(DeploymentStatusType.FAILED, provisioned.getStatus().type(), provisioned.getStatus().message());

        String hostname = uiProperties().resolveHostname(SITE_LABEL);
        CdnManagementClient cdn = cdnClient();
        AfdDomainInner domain = cdn.getAfdCustomDomains().get(resourceGroup(), profileName(), SITE_LABEL);
        assertEquals(hostname, domain.hostname());
        RouteInner route = cdn.getRoutes().get(resourceGroup(), profileName(), endpointName(cdn), SITE_LABEL);
        assertEquals("/" + OrganizationStorageProvisioner.UI_CONTAINER + "/" + UiStoragePaths.uiPrefix(APPLICATION_ID, UI_NAME),
                     route.originPath());

        DnsZone zone = DnsZoneManager.authenticate(credential, profileOf(ResourceId.fromString(uiProperties().getDnsZoneId()).subscriptionId()))
                                     .zones()
                                     .getById(uiProperties().getDnsZoneId());
        String suffix = recordSuffix(uiProperties().getSitesDomain(), zone.name());
        CnameRecordSet cname = zone.cNameRecordSets().getByName(SITE_LABEL + suffix);
        assertEquals(uiProperties().getFrontDoorEndpointHostName().toLowerCase(), cname.canonicalName().toLowerCase());
        assertNotNull(zone.txtRecordSets().getByName("_dnsauth." + SITE_LABEL + suffix), "the validation TXT record exists");
    }

    private <T> T await(Future<T> future) {
        T ret = null;
        try {
            ret = future.toCompletionStage().toCompletableFuture().get(STEP_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (ExecutionException e) {
            fail(e.getCause().getMessage(), e.getCause());
        } catch (Exception e) {
            fail(e);
        }
        return ret;
    }

    private OrganizationStorageProperties storageProperties() {
        return properties.getSystemApi().getOrganizationStorage();
    }

    private UiDeploymentProperties uiProperties() {
        return properties.getSystemApi().getUiDeployment();
    }

    private String resourceGroup() {
        return ResourceId.fromString(uiProperties().getFrontDoorProfileId()).resourceGroupName();
    }

    private String profileName() {
        return ResourceId.fromString(uiProperties().getFrontDoorProfileId()).name();
    }

    private CdnManagementClient cdnClient() {
        return CdnManager.authenticate(credential, profileOf(ResourceId.fromString(uiProperties().getFrontDoorProfileId()).subscriptionId()))
                         .serviceClient();
    }

    private String endpointName(CdnManagementClient cdn) {
        String host = uiProperties().getFrontDoorEndpointHostName();
        return cdn.getAfdEndpoints().listByProfile(resourceGroup(), profileName()).stream()
                  .filter(endpoint -> host.equalsIgnoreCase(endpoint.hostname()))
                  .map(AfdEndpointInner::name)
                  .findFirst()
                  .orElseThrow(() -> new AssertionError("Front Door profile " + profileName() + " has no endpoint with host name " + host));
    }

    private static AzureProfile profileOf(String subscriptionId) {
        return new AzureProfile(null, subscriptionId, AzureEnvironment.AZURE);
    }

    /** The sites domain relative to the zone, as the provisioner names records: {@code .apps-local} for {@code apps-local.kinotic.ai}. */
    private static String recordSuffix(String sitesDomain, String zoneName) {
        return sitesDomain.equalsIgnoreCase(zoneName)
                ? ""
                : "." + sitesDomain.substring(0, sitesDomain.length() - zoneName.length() - 1);
    }

    /** Binds the two property blocks the provisioners read from the local profile's YAML. */
    @SuppressWarnings("unchecked")
    private static KinoticSystemApiProperties load(Path localProfile) throws IOException {
        Map<String, Object> yaml;
        try (Reader reader = Files.newBufferedReader(localProfile)) {
            yaml = new Yaml().load(reader);
        }
        Map<String, Object> systemApi = (Map<String, Object>) ((Map<String, Object>) yaml.get("kinotic")).get("systemApi");
        Map<String, Object> storage = (Map<String, Object>) systemApi.get("organizationStorage");
        Map<String, Object> sites = (Map<String, Object>) systemApi.get("uiDeployment");

        KinoticSystemApiProperties ret = new KinoticSystemApiProperties();
        ret.getSystemApi().getOrganizationStorage()
           .setDisableProvisioner(Boolean.TRUE.equals(storage.get("disableProvisioner")))
           // the development profile turns private endpoints off for every developer machine; the local profile inherits that
           .setDisablePrivateEndpoint(!Boolean.FALSE.equals(storage.get("disablePrivateEndpoint")))
           .setSubscriptionIds((List<String>) storage.get("subscriptionIds"))
           .setResourceGroup((String) storage.get("resourceGroup"))
           .setLocation((String) storage.get("location"));
        ret.getSystemApi().getUiDeployment()
           .setDisableProvisioner(Boolean.TRUE.equals(sites.get("disableProvisioner")))
           .setSitesDomain((String) sites.get("sitesDomain"))
           .setDnsZoneId((String) sites.get("dnsZoneId"))
           .setFrontDoorProfileId((String) sites.get("frontDoorProfileId"))
           .setFrontDoorEndpointHostName((String) sites.get("frontDoorEndpointHostName"));
        return ret;
    }

}
