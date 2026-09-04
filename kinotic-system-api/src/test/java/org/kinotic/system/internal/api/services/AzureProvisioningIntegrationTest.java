package org.kinotic.system.internal.api.services;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
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
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final String COMMIT_SHA = "0000000000000000000000000000000000000000";
    private static final String INDEX_HTML = "<!doctype html><title>azure-it</title>";
    /** A storage account or a Front Door write takes minutes; a step past this is stuck. */
    private static final long STEP_TIMEOUT_MINUTES = 15;
    /** A site serves once its configuration reaches Front Door's edges, which Microsoft bounds at 15 minutes per change and longer when changes queue. */
    private static final long SITE_TIMEOUT_MINUTES = 45;
    private static final long SITE_POLL_MS = 30_000;

    private Vertx vertx;
    private KinoticSystemApiProperties properties;
    private TokenCredential credential;
    private AzureOrganizationStorageProvisioner storageProvisioner;
    private AzureOrganizationStorageService storageService;
    private FrontDoorUiDeploymentProvisioner siteProvisioner;
    private final HttpClient http = HttpClient.newHttpClient();
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
        storageService = new AzureOrganizationStorageService(vertx, properties);
        siteProvisioner = new FrontDoorUiDeploymentProvisioner(vertx, properties, new StubUiDeploymentRepository());
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
    void preparesFrontDoorForTheOrganization() throws Exception {
        assumeTrue(organization != null, "the storage step did not complete");

        await(siteProvisioner.prepareOrganization(organization));

        CdnManagementClient cdn = cdnClient();
        // the SDK's client predates origin authentication, so the group is read at the API version that has it
        String token = credential.getToken(new TokenRequestContext().addScopes("https://management.azure.com/.default")).block().getToken();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://management.azure.com" + uiProperties().getFrontDoorProfileId()
                                                                        + "/originGroups/org-" + ORGANIZATION_ID + "?api-version=2025-06-01"))
                                         .header("Authorization", "Bearer " + token)
                                         .build();
        HttpResponse<String> group = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, group.statusCode(), "the organization's origin group exists: " + group.body());
        JsonObject authentication = new JsonObject(group.body()).getJsonObject("properties").getJsonObject("authentication");
        assertNotNull(authentication, "the origin group authenticates as the profile's identity");
        assertEquals("SystemAssignedIdentity", authentication.getString("type"));

        RuleInner rule = cdn.getRules().get(resourceGroup(), profileName(), "sites", "spa");
        UrlRewriteAction rewrite = (UrlRewriteAction) rule.actions().getFirst();
        assertEquals("/index.html", rewrite.parameters().destination(), "the spa rule rewrites to the index");
    }

    @Test
    @Order(3)
    @Timeout(value = SITE_TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void provisionsASite() throws Exception {
        assumeTrue(organization != null, "the storage step did not complete");
        // the files go up before the site is provisioned, as the publish task orders it
        publish(UI_NAME + "/version.json", new JsonObject().put("commitSha", COMMIT_SHA).encode(), "application/json", COMMIT_SHA);
        publish(UI_NAME + "/index.html", INDEX_HTML, "text/html", COMMIT_SHA);
        // a file an older publish left behind goes with the finalize step's cleanup, the current commit's files stay
        String stale = UI_NAME + "/assets/old-" + COMMIT_SHA.substring(0, 8) + ".js";
        publish(stale, "// stale", "text/javascript", "1".repeat(40));
        String uiPrefix = UiStoragePaths.uiPrefix(APPLICATION_ID, UI_NAME);
        await(storageService.deleteFilesOfOtherCommits(organization, uiPrefix, COMMIT_SHA));
        assertFalse(blob(uiPrefix + "/" + stale.substring(UI_NAME.length() + 1)).exists(), "the other commit's file is deleted");
        assertTrue(blob(uiPrefix + "/index.html").exists(), "the current commit's file stays");
        UiDeployment deployment = new UiDeployment().setId(SITE_LABEL)
                                                    .setOrganizationId(ORGANIZATION_ID)
                                                    .setApplicationId(APPLICATION_ID)
                                                    .setProjectId(PROJECT_ID)
                                                    .setName(UI_NAME)
                                                    .setCommitSha(COMMIT_SHA)
                                                    .setStatus(new DeploymentStatus(DeploymentStatusType.PROVISIONING))
                                                    .setCreated(new Date())
                                                    .setUpdated(new Date());

        UiDeployment provisioned = await(siteProvisioner.provision(deployment, organization));

        // ready only once the site serves the commit through Front Door, minutes after a new hostname's records resolve
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

        UiDeployment site = provisioned;
        while (site.getStatus().type() == DeploymentStatusType.PROVISIONING) {
            Thread.sleep(SITE_POLL_MS);
            site = await(siteProvisioner.checkProvisioning(site));
        }
        assertEquals(DeploymentStatusType.READY, site.getStatus().type(), site.getStatus().message());

        // a route or origin group written moments ago is still propagating to the edges, and an
        // earlier configuration of the same site may answer meanwhile, so each check is retried
        String url = uiProperties().resolveSiteUrl(SITE_LABEL);
        assertServes(url + "/version.json", new JsonObject().put("commitSha", COMMIT_SHA).encode(), "a file is served as it is");
        assertServes(url + "/", INDEX_HTML, "the spa rule serves index.html at the root");
        assertServes(url + "/some/route", INDEX_HTML, "the spa rule serves index.html for a route");
        assertServes(url + "/?code=abc&state=xyz", INDEX_HTML, "a query string, as an OAuth callback carries, reaches the index");
        assertEquals(404, get(url + "/missing.js").statusCode(), "a file that is not published is not the index");
    }

    private HttpResponse<String> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertServes(String url, String expected, String message) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(SITE_TIMEOUT_MINUTES);
        String body = get(url).body();
        while (!expected.equals(body) && System.currentTimeMillis() < deadline) {
            Thread.sleep(SITE_POLL_MS);
            body = get(url).body();
        }
        assertEquals(expected, body, message);
    }

    /** Uploads one file of the UI the way the publish workload does: through the application's upload URL, uncached, stamped with its commit. */
    private void publish(String path, String content, String contentType, String commitSha) {
        String uploadUrl = await(storageService.issueUploadUrl(organization, APPLICATION_ID, Duration.ofMinutes(STEP_TIMEOUT_MINUTES)));
        int query = uploadUrl.indexOf('?');
        BlobClient blob = new BlobClientBuilder().endpoint(uploadUrl.substring(0, query) + "/" + path + uploadUrl.substring(query))
                                                 .buildClient();
        blob.upload(BinaryData.fromString(content), true);
        blob.setHttpHeaders(new BlobHttpHeaders().setContentType(contentType).setCacheControl("no-cache"));
        blob.setMetadata(Map.of("commit", commitSha));
    }

    /** A blob of the organization's container, read as the test's own identity. */
    private BlobClient blob(String name) {
        return new BlobClientBuilder().endpoint(organization.getStorage().getAzureBlobEndpoint())
                                      .containerName(OrganizationStorageProvisioner.UI_CONTAINER)
                                      .blobName(name)
                                      .credential(credential)
                                      .buildClient();
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
