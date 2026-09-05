package org.kinotic.system.internal.api.services;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.cdn.CdnManager;
import com.azure.resourcemanager.cdn.fluent.CdnManagementClient;
import com.azure.resourcemanager.cdn.fluent.models.AfdDomainInner;
import com.azure.resourcemanager.cdn.fluent.models.AfdEndpointInner;
import com.azure.resourcemanager.cdn.fluent.models.AfdOriginInner;
import com.azure.resourcemanager.cdn.fluent.models.RouteInner;
import com.azure.resourcemanager.cdn.fluent.models.RuleInner;
import com.azure.resourcemanager.cdn.models.ActivatedResourceReference;
import com.azure.resourcemanager.cdn.models.AfdCertificateType;
import com.azure.resourcemanager.cdn.models.AfdDomainHttpsParameters;
import com.azure.resourcemanager.cdn.models.AfdEndpointProtocols;
import com.azure.resourcemanager.cdn.models.AfdMinimumTlsVersion;
import com.azure.resourcemanager.cdn.models.AfdProvisioningState;
import com.azure.resourcemanager.cdn.models.AfdQueryStringCachingBehavior;
import com.azure.resourcemanager.cdn.models.AfdRouteCacheConfiguration;
import com.azure.resourcemanager.cdn.models.CompressionSettings;
import com.azure.resourcemanager.cdn.models.DeliveryRuleUrlFileExtensionCondition;
import com.azure.resourcemanager.cdn.models.DomainValidationState;
import com.azure.resourcemanager.cdn.models.EnabledState;
import com.azure.resourcemanager.cdn.models.ForwardingProtocol;
import com.azure.resourcemanager.cdn.models.HttpsRedirect;
import com.azure.resourcemanager.cdn.models.LinkToDefaultDomain;
import com.azure.resourcemanager.cdn.models.MatchProcessingBehavior;
import com.azure.resourcemanager.cdn.models.ResourceReference;
import com.azure.resourcemanager.cdn.models.UrlFileExtensionMatchConditionParameters;
import com.azure.resourcemanager.cdn.models.UrlFileExtensionOperator;
import com.azure.resourcemanager.cdn.models.UrlRewriteAction;
import com.azure.resourcemanager.cdn.models.UrlRewriteActionParameters;
import com.azure.resourcemanager.dns.DnsZoneManager;
import com.azure.resourcemanager.dns.models.DnsZone;
import com.azure.resourcemanager.dns.models.TxtRecordSet;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.DeploymentStatus;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.config.UiDeploymentProperties;
import org.kinotic.system.api.services.OrganizationStorageProvisioner;
import org.kinotic.system.api.services.UiDeploymentProvisioner;
import org.kinotic.system.api.services.UiStoragePaths;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

/**
 * Serves each published UI through the platform's Front Door Standard profile, at
 * {@code <label>.<sitesDomain>}. Per organization, created with the organization once its
 * storage is ready, an origin group on the storage account that authenticates to it as the
 * profile's managed identity. Shared by every site, a rule set that routes requests naming no
 * file to {@code index.html}. Every site gets a custom domain with a managed certificate, its
 * CNAME and validation TXT records in the platform's DNS zone, and a route from its domain to
 * the UI's prefix in the container. A site is provisioning until it serves the deployment's
 * commit at its hostname, which the provisioner keeps checking in the background.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.systemApi.uiDeployment.disableProvisioner",
                       havingValue = "false", matchIfMissing = true)
public class FrontDoorUiDeploymentProvisioner implements UiDeploymentProvisioner {

    private static final String ORIGIN_NAME = "blob";
    private static final String RULE_SET_NAME = "sites";
    private static final String SPA_RULE = "spa";
    private static final String VALIDATION_RECORD_PREFIX = "_dnsauth.";
    private static final String VERSION_FILE = "version.json";
    /** The audience of the token the profile presents to the storage account. */
    private static final String STORAGE_SCOPE = "https://storage.azure.com/.default";
    /** Origin authentication exists from this API version on, which the SDK's client does not speak yet. */
    private static final String ORIGIN_GROUP_API_VERSION = "2025-06-01";
    /** Container properties answer 200 to the identity's token; the longest interval Front Door allows. */
    private static final String PROBE_PATH = "/" + OrganizationStorageProvisioner.UI_CONTAINER + "?restype=container";
    private static final int PROBE_INTERVAL_SECONDS = 240;
    private static final List<String> COMPRESSED_CONTENT_TYPES = List.of(
            "text/html", "text/css", "text/plain", "text/xml", "text/javascript", "application/javascript",
            "application/x-javascript", "application/json", "application/xml", "application/wasm",
            "image/svg+xml", "application/manifest+json");
    /** Front Door answers 409 while a write on the profile is in flight; a retry waits this long times the attempt. */
    private static final long CONFLICT_BACKOFF_MS = 10_000;
    private static final int CONFLICT_ATTEMPTS = 6;
    private static final long PROVISIONING_POLL_MS = 5_000;
    private static final long HTTP_TIMEOUT_MS = 10_000;
    private static final long POLL_INTERVAL_MS = 30_000;
    /** Validation, the certificate and the configuration's propagation take minutes; past this the site stays provisioning until it is checked again. */
    private static final long POLL_TIMEOUT_MS = 2 * 60 * 60_000;

    private final Vertx vertx;
    private final KinoticSystemApiProperties kinoticProperties;
    private final UiDeploymentRepository uiDeploymentRepository;
    // On AKS this resolves to the kinotic-server workload identity, which holds CDN Profile
    // Contributor on the profile and DNS Zone Contributor on the zone
    private final TokenCredential credential = new DefaultAzureCredentialBuilder().build();
    // Set once by ensureClients, which every entry point calls first
    private CdnManagementClient cdn;
    private DnsZoneManager dns;
    private WebClient web;
    private String resourceGroup;
    private String profileName;
    private String recordSuffix;
    private volatile String endpointName;
    /** The tail of the profile's write queue; each write waits for the one before it. */
    private Future<Void> writes = Future.succeededFuture();

    @Override
    public Future<Void> prepareOrganization(Organization organization) {
        requireStorage(organization);
        ensureClients();
        log.info("Preparing Front Door for organization {}", organization.getId());
        return Future.all(ensureOriginGroup(organization), ensureRuleSet()).mapEmpty();
    }

    @Override
    public Future<UiDeployment> provision(UiDeployment deployment, Organization organization) {
        Validate.notNull(deployment, "deployment is required");
        requireStorage(organization);
        ensureClients();
        String label = deployment.getId();
        String hostname = properties().resolveHostname(label);
        log.info("Provisioning site {} for UI {} of project {}", hostname, deployment.getName(), deployment.getProjectId());
        // the organization's origin group and the shared rule set exist since the organization's creation; the route names them by id
        String originGroupId = originGroupId(organization);
        return ensureDomain(label, hostname)
                .compose(domain -> ensureDnsRecords(label, domain)
                        .compose(v -> ensureRoute(deployment, domain, originGroupId))
                        .map(v -> domain))
                .compose(domain -> statusOf(deployment, domain))
                .map(deployment::setStatus)
                .recover(error -> {
                    log.error("Site {} could not be provisioned", hostname, error);
                    return Future.succeededFuture(deployment.setStatus(
                            new DeploymentStatus(DeploymentStatusType.FAILED, error.getMessage())));
                })
                .onSuccess(row -> {
                    log.info("Site {} is {}", hostname, row.getStatus().type());
                    if (row.getStatus().type() == DeploymentStatusType.PROVISIONING) {
                        schedulePoll(label, System.currentTimeMillis() + POLL_TIMEOUT_MS);
                    }
                });
    }

    @Override
    public Future<UiDeployment> checkProvisioning(UiDeployment deployment) {
        Validate.notNull(deployment, "deployment is required");
        ensureClients();
        return AzureUtil.toFuture(AzureUtil.emptyIfNotFound(cdn.getAfdCustomDomains().getAsync(resourceGroup, profileName, deployment.getId())), vertx)
                        .compose(domain -> domain == null
                                ? Future.succeededFuture(new DeploymentStatus(DeploymentStatusType.FAILED,
                                                                              "The site's domain no longer exists; retry provisioning to create it again"))
                                : statusOf(deployment, domain))
                        .map(deployment::setStatus);
    }

    @Override
    public Future<Void> remove(UiDeployment deployment) {
        Validate.notNull(deployment, "deployment is required");
        ensureClients();
        String label = deployment.getId();
        log.info("Removing site {}", properties().resolveHostname(label));
        // the route holds the domain, so it goes first
        return endpointName()
                .compose(endpoint -> write(() -> AzureUtil.emptyIfNotFound(
                        cdn.getRoutes().deleteAsync(resourceGroup, profileName, endpoint, label))))
                .compose(v -> write(() -> AzureUtil.emptyIfNotFound(
                        cdn.getAfdCustomDomains().deleteAsync(resourceGroup, profileName, label))))
                .compose(v -> AzureUtil.toFuture(AzureUtil.emptyIfNotFound(dns.zones().getByIdAsync(properties().getDnsZoneId())
                        .flatMap(zone -> zone.update()
                                             .withoutCNameRecordSet(label + recordSuffix)
                                             .withoutTxtRecordSet(VALIDATION_RECORD_PREFIX + label + recordSuffix)
                                             .applyAsync())), vertx))
                .mapEmpty();
    }

    // Built on first use rather than at startup, so a server that never publishes a UI opens
    // no Azure client
    private synchronized void ensureClients() {
        if (cdn == null) {
            ResourceId profile = ResourceId.fromString(properties().getFrontDoorProfileId());
            ResourceId zone = ResourceId.fromString(properties().getDnsZoneId());
            recordSuffix = recordSuffix(properties().getSitesDomain(), zone.name());
            resourceGroup = profile.resourceGroupName();
            profileName = profile.name();
            cdn = CdnManager.authenticate(credential, new AzureProfile(null, profile.subscriptionId(), AzureEnvironment.AZURE))
                            .serviceClient();
            dns = DnsZoneManager.authenticate(credential, new AzureProfile(null, zone.subscriptionId(), AzureEnvironment.AZURE));
            web = WebClient.create(vertx);
        }
    }

    /** What follows a site's label in its record names: the sites domain relative to the zone, e.g. {@code .apps} for {@code apps.kinotic.ai} in {@code kinotic.ai}. */
    private static String recordSuffix(String sitesDomain, String zoneName) {
        String ret;
        if (sitesDomain.equalsIgnoreCase(zoneName)) {
            ret = "";
        } else if (sitesDomain.toLowerCase().endsWith("." + zoneName.toLowerCase())) {
            ret = "." + sitesDomain.substring(0, sitesDomain.length() - zoneName.length() - 1);
        } else {
            throw new IllegalStateException("kinotic.systemApi.uiDeployment.sitesDomain " + sitesDomain
                    + " is not within the DNS zone " + zoneName + " of kinotic.systemApi.uiDeployment.dnsZoneId");
        }
        return ret;
    }

    /**
     * The organization's origin group, authenticating to its storage account as the profile's
     * identity, and its one origin, the account's blob endpoint. A group without that
     * authentication is written again.
     */
    private Future<Void> ensureOriginGroup(Organization organization) {
        String groupName = originGroupName(organization);
        String groupPath = originGroupId(organization);
        String host = URI.create(organization.getStorage().getAzureBlobEndpoint()).getHost();
        JsonObject desired = originGroup();
        return arm(HttpMethod.GET, groupPath, null)
                .compose(existing -> {
                    Future<Void> ret;
                    if (existing != null && authenticatesAsDesired(existing, desired)) {
                        ret = Future.succeededFuture();
                    } else {
                        ret = write(() -> armCall(HttpMethod.PUT, groupPath, desired).then(awaitOriginGroup(groupName)));
                    }
                    return ret;
                })
                .compose(v -> getOrCreate(cdn.getAfdOrigins().getAsync(resourceGroup, profileName, groupName, ORIGIN_NAME),
                                          () -> cdn.getAfdOrigins().createAsync(resourceGroup, profileName, groupName, ORIGIN_NAME,
                                                                                new AfdOriginInner()
                                                                                        .withHostname(host)
                                                                                        .withOriginHostHeader(host)
                                                                                        .withHttpPort(80)
                                                                                        .withHttpsPort(443)
                                                                                        .withPriority(1)
                                                                                        .withWeight(1000)
                                                                                        .withEnabledState(EnabledState.ENABLED)
                                                                                        .withEnforceCertificateNameCheck(true))))
                .mapEmpty();
    }

    private static boolean authenticatesAsDesired(JsonObject existing, JsonObject desired) {
        JsonObject actual = existing.getJsonObject("properties").getJsonObject("authentication");
        JsonObject wanted = desired.getJsonObject("properties").getJsonObject("authentication");
        return actual != null
                && wanted.getString("type").equals(actual.getString("type"))
                && wanted.getString("scope").equals(actual.getString("scope"));
    }

    // Origin authentication requires HTTPS health probes on the group; the SDK's models
    // predate the authentication property, so the group is written as the API's JSON
    private static JsonObject originGroup() {
        return new JsonObject().put("properties", new JsonObject()
                .put("loadBalancingSettings", new JsonObject()
                        .put("sampleSize", 4)
                        .put("successfulSamplesRequired", 3)
                        .put("additionalLatencyInMilliseconds", 50))
                .put("healthProbeSettings", new JsonObject()
                        .put("probePath", PROBE_PATH)
                        .put("probeRequestType", "HEAD")
                        .put("probeProtocol", "Https")
                        .put("probeIntervalInSeconds", PROBE_INTERVAL_SECONDS))
                .put("sessionAffinityState", "Disabled")
                .put("authentication", new JsonObject()
                        .put("type", "SystemAssignedIdentity")
                        .put("scope", STORAGE_SCOPE)));
    }

    // The PUT is accepted before the group is provisioned; the SDK's read reports when it is
    private Mono<Void> awaitOriginGroup(String groupName) {
        return cdn.getAfdOriginGroups().getAsync(resourceGroup, profileName, groupName)
                  .flatMap(group -> {
                      Mono<Void> ret;
                      AfdProvisioningState state = group.provisioningState();
                      if (AfdProvisioningState.SUCCEEDED.equals(state)) {
                          ret = Mono.empty();
                      } else if (AfdProvisioningState.FAILED.equals(state)) {
                          ret = Mono.error(new IllegalStateException("Origin group " + groupName + " failed to provision"));
                      } else {
                          ret = Mono.delay(Duration.ofMillis(PROVISIONING_POLL_MS)).then(Mono.defer(() -> awaitOriginGroup(groupName)));
                      }
                      return ret;
                  });
    }

    private Future<JsonObject> arm(HttpMethod method, String path, JsonObject body) {
        return AzureUtil.toFuture(armCall(method, path, body), vertx);
    }

    /**
     * A call on the profile's resources at {@link #ORIGIN_GROUP_API_VERSION}, through the SDK's
     * authenticated pipeline. Emits the response body, nothing for a 404, and fails with the
     * management plane's exception otherwise, so a busy profile's 409 is retried like any write.
     */
    private Mono<JsonObject> armCall(HttpMethod method, String path, JsonObject body) {
        HttpRequest request = new HttpRequest(method, cdn.getEndpoint() + path + "?api-version=" + ORIGIN_GROUP_API_VERSION);
        if (body != null) {
            request.setBody(body.encode()).setHeader(HttpHeaderName.CONTENT_TYPE, "application/json");
        }
        return cdn.getHttpPipeline().send(request)
                  .flatMap(response -> response.getBodyAsString().defaultIfEmpty("").flatMap(text -> {
                      Mono<JsonObject> ret;
                      int status = response.getStatusCode();
                      if (status == 404) {
                          ret = Mono.empty();
                      } else if (status >= 200 && status < 300) {
                          ret = Mono.just(text.isEmpty() ? new JsonObject() : new JsonObject(text));
                      } else {
                          ret = Mono.error(new ManagementException(method + " " + path + " answered " + status + ": " + text, response));
                      }
                      return ret;
                  }));
    }

    /** The rule set every route shares and its one rule, each created when missing. */
    private Future<Void> ensureRuleSet() {
        return getOrCreate(cdn.getRuleSets().getAsync(resourceGroup, profileName, RULE_SET_NAME),
                           () -> cdn.getRuleSets().createAsync(resourceGroup, profileName, RULE_SET_NAME))
                .compose(ruleSet -> getOrCreate(cdn.getRules().getAsync(resourceGroup, profileName, RULE_SET_NAME, SPA_RULE),
                                                () -> cdn.getRules().createAsync(resourceGroup, profileName, RULE_SET_NAME, SPA_RULE, spaRule())))
                .mapEmpty();
    }

    /**
     * Rewrites a request whose path names no file, a route of the single-page application, to
     * its {@code index.html}; a request naming a file reaches the origin as it is.
     */
    private static RuleInner spaRule() {
        // Front Door validates the destination as a literal that must begin with "/".
        // UrlFileExtension Any matches a path with no extension as well, so it cannot tell a
        // file from a route; an extension longer than zero can
        return new RuleInner()
                .withOrder(1)
                .withConditions(List.of(new DeliveryRuleUrlFileExtensionCondition()
                        .withParameters(new UrlFileExtensionMatchConditionParameters()
                                .withOperator(UrlFileExtensionOperator.GREATER_THAN)
                                .withNegateCondition(true)
                                .withMatchValues(List.of("0")))))
                .withActions(List.of(new UrlRewriteAction()
                        .withParameters(new UrlRewriteActionParameters()
                                .withSourcePattern("/")
                                .withDestination("/index.html")
                                .withPreserveUnmatchedPath(false))))
                .withMatchProcessingBehavior(MatchProcessingBehavior.STOP);
    }

    /**
     * The site's custom domain with a managed certificate. A domain whose validation was
     * rejected or timed out gets a new token, which the records written next carry.
     */
    private Future<AfdDomainInner> ensureDomain(String label, String hostname) {
        return getOrCreate(cdn.getAfdCustomDomains().getAsync(resourceGroup, profileName, label),
                           () -> cdn.getAfdCustomDomains().createAsync(resourceGroup, profileName, label, new AfdDomainInner()
                                   .withHostname(hostname)
                                   .withTlsSettings(new AfdDomainHttpsParameters()
                                           .withCertificateType(AfdCertificateType.MANAGED_CERTIFICATE)
                                           .withMinimumTlsVersion(AfdMinimumTlsVersion.TLS12))))
                .compose(domain -> {
                    Future<AfdDomainInner> ret;
                    if (validationLapsed(domain.domainValidationState())) {
                        log.info("Validation of {} {}, requesting a new token", hostname, domain.domainValidationState());
                        ret = write(() -> cdn.getAfdCustomDomains().refreshValidationTokenAsync(resourceGroup, profileName, label))
                                .compose(v -> AzureUtil.toFuture(cdn.getAfdCustomDomains().getAsync(resourceGroup, profileName, label), vertx));
                    } else {
                        ret = Future.succeededFuture(domain);
                    }
                    return ret;
                });
    }

    private static boolean validationLapsed(DomainValidationState state) {
        return DomainValidationState.REJECTED.equals(state) || DomainValidationState.TIMED_OUT.equals(state);
    }

    /** The site's CNAME to the profile's endpoint and the TXT record carrying its validation token. */
    private Future<Void> ensureDnsRecords(String label, AfdDomainInner domain) {
        String token = domain.validationProperties() != null ? domain.validationProperties().validationToken() : null;
        Validate.notBlank(token, "Front Door issued no validation token for %s", domain.hostname());
        String cnameName = label + recordSuffix;
        String txtName = VALIDATION_RECORD_PREFIX + label + recordSuffix;
        return AzureUtil.toFuture(dns.zones().getByIdAsync(properties().getDnsZoneId()), vertx)
                .compose(zone -> ensureCname(zone, cnameName).compose(v -> ensureValidationText(zone, txtName, token)));
    }

    private Future<Void> ensureCname(DnsZone zone, String name) {
        String target = properties().getFrontDoorEndpointHostName();
        return AzureUtil.toFuture(AzureUtil.emptyIfNotFound(zone.cNameRecordSets().getByNameAsync(name)), vertx)
                .compose(existing -> {
                    Future<DnsZone> ret;
                    if (existing == null) {
                        ret = AzureUtil.toFuture(zone.update().defineCNameRecordSet(name).withAlias(target).attach().applyAsync(), vertx);
                    } else if (target.equalsIgnoreCase(existing.canonicalName())) {
                        ret = Future.succeededFuture(zone);
                    } else {
                        ret = AzureUtil.toFuture(zone.update().updateCNameRecordSet(name).withAlias(target).parent().applyAsync(), vertx);
                    }
                    return ret.mapEmpty();
                });
    }

    private Future<Void> ensureValidationText(DnsZone zone, String name, String token) {
        return AzureUtil.toFuture(AzureUtil.emptyIfNotFound(zone.txtRecordSets().getByNameAsync(name)), vertx)
                .compose(existing -> {
                    Future<DnsZone> ret;
                    if (existing == null) {
                        ret = AzureUtil.toFuture(zone.update().defineTxtRecordSet(name).withText(token).attach().applyAsync(), vertx);
                    } else if (hasText(existing, token)) {
                        ret = Future.succeededFuture(zone);
                    } else {
                        // a refreshed token replaces the set, so no lapsed token lingers in it
                        ret = AzureUtil.toFuture(zone.update().withoutTxtRecordSet(name).applyAsync(), vertx)
                                       .compose(v -> AzureUtil.toFuture(zone.update().defineTxtRecordSet(name).withText(token).attach().applyAsync(), vertx));
                    }
                    return ret.mapEmpty();
                });
    }

    private static boolean hasText(TxtRecordSet recordSet, String text) {
        return recordSet.records().stream().anyMatch(record -> text.equals(String.join("", record.value())));
    }

    /**
     * The site's route: its domain, every path, HTTPS only, the UI's prefix in the container as
     * origin path, the shared rule set, cached. An existing route naming another rule set is
     * written again.
     */
    private Future<Void> ensureRoute(UiDeployment deployment, AfdDomainInner domain, String originGroupId) {
        String label = deployment.getId();
        String ruleSetId = properties().getFrontDoorProfileId() + "/ruleSets/" + RULE_SET_NAME;
        RouteInner desired = route(deployment, domain, originGroupId, ruleSetId);
        return endpointName()
                .compose(endpoint -> AzureUtil.toFuture(AzureUtil.emptyIfNotFound(cdn.getRoutes().getAsync(resourceGroup, profileName, endpoint, label)), vertx)
                        .compose(existing -> {
                            Future<Void> ret;
                            if (existing != null && existing.ruleSets() != null && existing.ruleSets().size() == 1
                                    && ruleSetId.equalsIgnoreCase(existing.ruleSets().getFirst().id())) {
                                ret = Future.succeededFuture();
                            } else {
                                ret = write(() -> cdn.getRoutes().createAsync(resourceGroup, profileName, endpoint, label, desired)).mapEmpty();
                            }
                            return ret;
                        }));
    }

    private static RouteInner route(UiDeployment deployment, AfdDomainInner domain, String originGroupId, String ruleSetId) {
        String originPath = "/" + OrganizationStorageProvisioner.UI_CONTAINER + "/"
                + UiStoragePaths.uiPrefix(deployment.getApplicationId(), deployment.getName());
        return new RouteInner()
                .withCustomDomains(List.of(new ActivatedResourceReference().withId(domain.id())))
                .withOriginGroup(new ResourceReference().withId(originGroupId))
                .withOriginPath(originPath)
                .withRuleSets(List.of(new ResourceReference().withId(ruleSetId)))
                .withSupportedProtocols(List.of(AfdEndpointProtocols.HTTP, AfdEndpointProtocols.HTTPS))
                .withPatternsToMatch(List.of("/*"))
                // origin authentication requires HTTPS to the origin
                .withForwardingProtocol(ForwardingProtocol.HTTPS_ONLY)
                .withLinkToDefaultDomain(LinkToDefaultDomain.DISABLED)
                .withHttpsRedirect(HttpsRedirect.ENABLED)
                .withCacheConfiguration(new AfdRouteCacheConfiguration()
                        .withQueryStringCachingBehavior(AfdQueryStringCachingBehavior.IGNORE_QUERY_STRING)
                        .withCompressionSettings(new CompressionSettings()
                                .withIsCompressionEnabled(true)
                                .withContentTypesToCompress(COMPRESSED_CONTENT_TYPES)))
                .withEnabledState(EnabledState.ENABLED);
    }

    /** The name of the profile's endpoint, resolved once from the configured host name. */
    private Future<String> endpointName() {
        Future<String> ret;
        if (endpointName != null) {
            ret = Future.succeededFuture(endpointName);
        } else {
            String host = properties().getFrontDoorEndpointHostName();
            ret = AzureUtil.toFuture(cdn.getAfdEndpoints().listByProfileAsync(resourceGroup, profileName)
                                        .filter(endpoint -> host.equalsIgnoreCase(endpoint.hostname()))
                                        .next()
                                        .map(AfdEndpointInner::name), vertx)
                    .compose(name -> name == null
                            ? Future.failedFuture(new IllegalStateException("Front Door profile " + profileName
                                    + " has no endpoint with host name " + host))
                            : Future.succeededFuture(name))
                    .onSuccess(name -> endpointName = name);
        }
        return ret;
    }

    /**
     * The site's status: failed when its domain's validation cannot succeed, ready once
     * {@code version.json} at its hostname answers with the deployment's commit and the root
     * answers with the index, and provisioning, with what was observed, until then.
     */
    private Future<DeploymentStatus> statusOf(UiDeployment deployment, AfdDomainInner domain) {
        DomainValidationState validation = domain.domainValidationState();
        Future<DeploymentStatus> ret;
        if (validationLapsed(validation) || DomainValidationState.INTERNAL_ERROR.equals(validation)) {
            ret = Future.succeededFuture(new DeploymentStatus(DeploymentStatusType.FAILED, "Validation of " + domain.hostname() + " "
                    + validation + "; retry provisioning to validate again"));
        } else {
            ret = servingStatus(deployment);
        }
        return ret;
    }

    // Front Door reports a domain approved and its certificate deployed before the route
    // serves, and a configuration change takes up to 15 minutes to reach its edges, longer
    // when changes queue, so only requests through the site tell that it serves: the version
    // file for the commit, and the root for the spa rule, which the version file bypasses
    private Future<DeploymentStatus> servingStatus(UiDeployment deployment) {
        String site = properties().resolveSiteUrl(deployment.getId());
        String versionUrl = site + "/" + VERSION_FILE;
        String rootUrl = site + "/";
        return web.getAbs(versionUrl).timeout(HTTP_TIMEOUT_MS).send()
                  .compose(version -> {
                      Future<DeploymentStatus> ret;
                      String served = version.statusCode() == 200 ? servedCommit(version) : null;
                      if (served != null && served.equals(deployment.getCommitSha())) {
                          ret = web.getAbs(rootUrl).timeout(HTTP_TIMEOUT_MS).send()
                                   .map(root -> servesHtml(root)
                                           ? new DeploymentStatus(DeploymentStatusType.READY)
                                           : new DeploymentStatus(DeploymentStatusType.PROVISIONING,
                                                                  rootUrl + " answered " + root.statusCode() + " " + root.getHeader("Content-Type")));
                      } else if (version.statusCode() == 200) {
                          ret = Future.succeededFuture(new DeploymentStatus(DeploymentStatusType.PROVISIONING,
                                                                            versionUrl + " serves commit " + served + ", not " + deployment.getCommitSha()));
                      } else {
                          ret = Future.succeededFuture(new DeploymentStatus(DeploymentStatusType.PROVISIONING,
                                                                            versionUrl + " answered " + version.statusCode()));
                      }
                      return ret;
                  })
                  .otherwise(error -> new DeploymentStatus(DeploymentStatusType.PROVISIONING, site + " is unreachable: " + error.getMessage()));
    }

    // The root unrewritten is the UI's directory, which the account answers with an empty 200
    private static boolean servesHtml(HttpResponse<Buffer> response) {
        String type = response.getHeader("Content-Type");
        return response.statusCode() == 200 && type != null && type.startsWith("text/html");
    }

    // A 200 that is not the version file, such as the index the spa rule serves, is not a commit
    private static String servedCommit(HttpResponse<Buffer> response) {
        String ret;
        try {
            ret = response.bodyAsJsonObject().getString("commitSha");
        } catch (RuntimeException e) {
            ret = null;
        }
        return ret;
    }

    /**
     * Checks a provisioning site every {@link #POLL_INTERVAL_MS} until it is ready or failed,
     * recording the outcome on its row, or until the deadline passes. A row removed or advanced
     * meanwhile ends the polling.
     */
    private void schedulePoll(String label, long deadline) {
        vertx.timer(POLL_INTERVAL_MS)
             .compose(v -> uiDeploymentRepository.findById(label))
             .compose(row -> {
                 Future<Void> ret;

                 if (row == null || row.getStatus().type() != DeploymentStatusType.PROVISIONING) {
                     ret = Future.succeededFuture();
                 } else {
                     ret = checkProvisioning(row).compose(checked -> {
                         Future<Void> saved;

                         if (checked.getStatus().type() != DeploymentStatusType.PROVISIONING) {
                             log.info("Site {} is {}", properties().resolveHostname(label), checked.getStatus().type());
                             saved = uiDeploymentRepository.save(checked.setUpdated(new Date())).mapEmpty();
                         } else if (System.currentTimeMillis() < deadline) {
                             schedulePoll(label, deadline);
                             saved = Future.succeededFuture();
                         } else {
                             log.warn("Site {} is still provisioning after {} minutes; it is checked again when listed",
                                      properties().resolveHostname(label), POLL_TIMEOUT_MS / 60_000);
                             saved = Future.succeededFuture();
                         }

                         return saved;
                     });
                 }

                 return ret;
             })
             .onFailure(error -> {
                 log.warn("Checking site {} failed", properties().resolveHostname(label), error);

                 if (System.currentTimeMillis() < deadline) {
                     schedulePoll(label, deadline);
                 }
             });
    }

    /** Reads the resource, creating it when it does not exist. Creation is a profile write. */
    private <T> Future<T> getOrCreate(Mono<T> get, Supplier<Mono<T>> create) {
        return AzureUtil.toFuture(AzureUtil.emptyIfNotFound(get), vertx)
                        .compose(existing -> existing != null ? Future.succeededFuture(existing) : write(create));
    }

    /**
     * Runs a write on the profile after every write queued before it, retrying with backoff
     * when the profile answers that another write is still in flight.
     */
    private synchronized <T> Future<T> write(Supplier<Mono<T>> write) {
        Future<T> ret = writes.transform((v, previousError) -> attempt(write, 1));
        writes = ret.otherwiseEmpty().mapEmpty();
        return ret;
    }

    private <T> Future<T> attempt(Supplier<Mono<T>> write, int attempt) {
        return AzureUtil.toFuture(write.get(), vertx).recover(error -> {
            Future<T> ret;
            if (AzureUtil.isConflict(error) && attempt < CONFLICT_ATTEMPTS) {
                log.debug("Front Door profile {} is busy, retrying the write in {}s", profileName, CONFLICT_BACKOFF_MS * attempt / 1000);
                ret = vertx.timer(CONFLICT_BACKOFF_MS * attempt).compose(v -> attempt(write, attempt + 1));
            } else {
                ret = Future.failedFuture(error);
            }
            return ret;
        });
    }

    private static String originGroupName(Organization organization) {
        return "org-" + organization.getId();
    }

    private String originGroupId(Organization organization) {
        return properties().getFrontDoorProfileId() + "/originGroups/" + originGroupName(organization);
    }

    private static void requireStorage(Organization organization) {
        Validate.notNull(organization, "organization is required");
        Validate.isTrue(organization.getStorage() != null && organization.getStorage().getAzureBlobEndpoint() != null,
                        "Organization %s has no storage endpoint recorded", organization.getId());
    }

    private UiDeploymentProperties properties() {
        return kinoticProperties.getSystemApi().getUiDeployment();
    }

}
