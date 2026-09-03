package org.kinotic.management.internal.api.services;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.cdn.CdnManager;
import com.azure.resourcemanager.cdn.fluent.CdnManagementClient;
import com.azure.resourcemanager.cdn.fluent.models.AfdDomainInner;
import com.azure.resourcemanager.cdn.fluent.models.AfdEndpointInner;
import com.azure.resourcemanager.cdn.fluent.models.AfdOriginGroupInner;
import com.azure.resourcemanager.cdn.fluent.models.AfdOriginInner;
import com.azure.resourcemanager.cdn.fluent.models.RouteInner;
import com.azure.resourcemanager.cdn.fluent.models.RuleInner;
import com.azure.resourcemanager.cdn.models.ActivatedResourceReference;
import com.azure.resourcemanager.cdn.models.AfdCertificateType;
import com.azure.resourcemanager.cdn.models.AfdDomainHttpsParameters;
import com.azure.resourcemanager.cdn.models.AfdEndpointProtocols;
import com.azure.resourcemanager.cdn.models.AfdMinimumTlsVersion;
import com.azure.resourcemanager.cdn.models.AfdQueryStringCachingBehavior;
import com.azure.resourcemanager.cdn.models.AfdRouteCacheConfiguration;
import com.azure.resourcemanager.cdn.models.CompressionSettings;
import com.azure.resourcemanager.cdn.models.DeliveryRuleUrlFileExtensionCondition;
import com.azure.resourcemanager.cdn.models.DeploymentStatus;
import com.azure.resourcemanager.cdn.models.DomainValidationState;
import com.azure.resourcemanager.cdn.models.EnabledState;
import com.azure.resourcemanager.cdn.models.ForwardingProtocol;
import com.azure.resourcemanager.cdn.models.HttpsRedirect;
import com.azure.resourcemanager.cdn.models.LinkToDefaultDomain;
import com.azure.resourcemanager.cdn.models.LoadBalancingSettingsParameters;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.management.api.config.UiDeploymentProperties;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.management.api.model.UiDeploymentStatus;
import org.kinotic.management.api.model.UiDeploymentStatusType;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.management.api.services.OrganizationStorageProvisioner;
import org.kinotic.management.api.services.OrganizationStorageService;
import org.kinotic.management.api.services.UiDeploymentProvisioner;
import org.kinotic.management.api.services.UiStoragePaths;
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
 * {@code <label>.<sitesDomain>}. What an organization's sites share is created with the
 * organization, once its storage is ready: an origin group on the storage account and a rule
 * set that routes requests naming a file to that file and everything else to
 * {@code index.html}, each with a read-only SAS on the {@code ui} container. Every site gets a custom domain with a managed
 * certificate, its CNAME and validation TXT records in the platform's DNS zone, and a route
 * from its domain to the UI's prefix in the container. A site is provisioning until Front
 * Door has validated its hostname and deployed its certificate, which the provisioner keeps
 * checking in the background.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.managementApi.uiDeployment.disableProvisioner",
                       havingValue = "false", matchIfMissing = true)
public class FrontDoorUiDeploymentProvisioner implements UiDeploymentProvisioner {

    private static final String ORIGIN_NAME = "blob";
    private static final String ASSET_RULE = "asset";
    private static final String SPA_RULE = "spa";
    private static final String VALIDATION_RECORD_PREFIX = "_dnsauth.";
    /** A rule is written once, with the SAS it carries; ten years outlasts any organization's sites. */
    private static final Duration READ_TOKEN_TTL = Duration.ofDays(10 * 365);
    private static final List<String> COMPRESSED_CONTENT_TYPES = List.of(
            "text/html", "text/css", "text/plain", "text/xml", "text/javascript", "application/javascript",
            "application/x-javascript", "application/json", "application/xml", "application/wasm",
            "image/svg+xml", "application/manifest+json");
    /** Front Door answers 409 while a write on the profile is in flight; a retry waits this long times the attempt. */
    private static final long CONFLICT_BACKOFF_MS = 10_000;
    private static final int CONFLICT_ATTEMPTS = 6;
    private static final long POLL_INTERVAL_MS = 30_000;
    /** Validation and the certificate take minutes once the records resolve; past this the site stays provisioning until it is checked again. */
    private static final long POLL_TIMEOUT_MS = 2 * 60 * 60_000;

    private final Vertx vertx;
    private final KinoticManagementApiProperties kinoticProperties;
    private final OrganizationStorageService organizationStorageService;
    private final UiDeploymentRepository uiDeploymentRepository;
    // On AKS this resolves to the kinotic-server workload identity, which holds CDN Profile
    // Contributor on the profile and DNS Zone Contributor on the zone
    private final TokenCredential credential = new DefaultAzureCredentialBuilder().build();
    // Set once by ensureClients, which every entry point calls first
    private CdnManagementClient cdn;
    private DnsZoneManager dns;
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
        return Future.all(ensureOriginGroup(organization), ensureRuleSet(organization)).mapEmpty();
    }

    @Override
    public Future<UiDeployment> provision(UiDeployment deployment, Organization organization) {
        Validate.notNull(deployment, "deployment is required");
        requireStorage(organization);
        ensureClients();
        String label = deployment.getId();
        String hostname = properties().resolveHostname(label);
        log.info("Provisioning site {} for UI {} of project {}", hostname, deployment.getName(), deployment.getProjectId());
        // the organization's origin group and rule set exist since its creation; the route names them by id
        String profileId = properties().getFrontDoorProfileId();
        String originGroupId = profileId + "/originGroups/" + originGroupName(organization);
        String ruleSetId = profileId + "/ruleSets/" + ruleSetName(organization);
        return ensureDomain(label, hostname)
                .compose(domain -> ensureDnsRecords(label, domain)
                        .compose(v -> ensureRoute(deployment, domain, originGroupId, ruleSetId))
                        .map(v -> domain))
                .map(domain -> deployment.setStatus(statusOf(domain)))
                .recover(error -> {
                    log.error("Site {} could not be provisioned", hostname, error);
                    return Future.succeededFuture(deployment.setStatus(
                            new UiDeploymentStatus(UiDeploymentStatusType.FAILED, error.getMessage())));
                })
                .onSuccess(row -> {
                    log.info("Site {} is {}", hostname, row.getStatus().type());
                    if (row.getStatus().type() == UiDeploymentStatusType.PROVISIONING) {
                        schedulePoll(label, System.currentTimeMillis() + POLL_TIMEOUT_MS);
                    }
                });
    }

    @Override
    public Future<UiDeployment> checkProvisioning(UiDeployment deployment) {
        Validate.notNull(deployment, "deployment is required");
        ensureClients();
        return AzureUtil.toFuture(AzureUtil.emptyIfNotFound(cdn.getAfdCustomDomains().getAsync(resourceGroup, profileName, deployment.getId())), vertx)
                        .map(domain -> deployment.setStatus(domain == null
                        ? new UiDeploymentStatus(UiDeploymentStatusType.FAILED,
                                                 "The site's domain no longer exists; retry provisioning to create it again")
                        : statusOf(domain)));
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
            throw new IllegalStateException("kinotic.managementApi.uiDeployment.sitesDomain " + sitesDomain
                    + " is not within the DNS zone " + zoneName + " of kinotic.managementApi.uiDeployment.dnsZoneId");
        }
        return ret;
    }

    /** The organization's origin group and its one origin, the storage account's blob endpoint. Emits the group's id. */
    private Future<String> ensureOriginGroup(Organization organization) {
        String groupName = originGroupName(organization);
        String host = URI.create(organization.getStorage().getBlobEndpoint()).getHost();
        return getOrCreate(cdn.getAfdOriginGroups().getAsync(resourceGroup, profileName, groupName),
                           () -> cdn.getAfdOriginGroups().createAsync(resourceGroup, profileName, groupName, new AfdOriginGroupInner()
                                   .withLoadBalancingSettings(new LoadBalancingSettingsParameters()
                                           .withSampleSize(4)
                                           .withSuccessfulSamplesRequired(3)
                                           .withAdditionalLatencyInMilliseconds(50))
                                   .withSessionAffinityState(EnabledState.DISABLED)))
                .compose(group -> getOrCreate(cdn.getAfdOrigins().getAsync(resourceGroup, profileName, groupName, ORIGIN_NAME),
                                              () -> cdn.getAfdOrigins().createAsync(resourceGroup, profileName, groupName, ORIGIN_NAME,
                                                                                    new AfdOriginInner()
                                                                                            .withHostname(host)
                                                                                            .withOriginHostHeader(host)
                                                                                            .withHttpPort(80)
                                                                                            .withHttpsPort(443)
                                                                                            .withPriority(1)
                                                                                            .withWeight(1000)
                                                                                            .withEnabledState(EnabledState.ENABLED)
                                                                                            .withEnforceCertificateNameCheck(true)))
                        .map(origin -> group.id()));
    }

    /** The organization's rule set and its two rules, each created when missing. Emits the rule set's id. */
    private Future<String> ensureRuleSet(Organization organization) {
        String ruleSetName = ruleSetName(organization);
        return getOrCreate(cdn.getRuleSets().getAsync(resourceGroup, profileName, ruleSetName),
                           () -> cdn.getRuleSets().createAsync(resourceGroup, profileName, ruleSetName))
                .compose(ruleSet -> ensureRule(organization, ruleSetName, ASSET_RULE)
                        .compose(v -> ensureRule(organization, ruleSetName, SPA_RULE))
                        .map(v -> ruleSet.id()));
    }

    // The SAS is minted only for a rule being written, so an existing rule costs one read
    private Future<Void> ensureRule(Organization organization, String ruleSetName, String ruleName) {
        return AzureUtil.toFuture(AzureUtil.emptyIfNotFound(cdn.getRules().getAsync(resourceGroup, profileName, ruleSetName, ruleName)), vertx)
                        .compose(existing -> {
                            Future<Void> ret;
                            if (existing != null) {
                                ret = Future.succeededFuture();
                            } else {
                                ret = organizationStorageService.issueReadToken(organization, READ_TOKEN_TTL)
                                        .compose(token -> write(() -> cdn.getRules().createAsync(
                                                resourceGroup, profileName, ruleSetName, ruleName, rule(ruleName, token))))
                                        .mapEmpty();
                            }
                            return ret;
                        });
    }

    /**
     * The asset rule rewrites a request whose path names a file to that file; the spa rule
     * rewrites one that names no file, a route of the single-page application, to its
     * {@code index.html}. Either way the SAS is appended for the origin.
     */
    private static RuleInner rule(String name, String token) {
        boolean spa = SPA_RULE.equals(name);
        return new RuleInner()
                .withOrder(spa ? 2 : 1)
                .withConditions(List.of(new DeliveryRuleUrlFileExtensionCondition()
                        .withParameters(new UrlFileExtensionMatchConditionParameters()
                                .withOperator(UrlFileExtensionOperator.ANY)
                                .withNegateCondition(spa)
                                .withMatchValues(List.of()))))
                .withActions(List.of(new UrlRewriteAction()
                        .withParameters(new UrlRewriteActionParameters()
                                .withSourcePattern("/")
                                .withDestination((spa ? "/index.html" : "{url_path}") + "?" + token)
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

    /** The site's route: its domain, every path, HTTPS only, the UI's prefix in the container as origin path, cached. */
    private Future<Void> ensureRoute(UiDeployment deployment, AfdDomainInner domain, String originGroupId, String ruleSetId) {
        String label = deployment.getId();
        String originPath = "/" + OrganizationStorageProvisioner.UI_CONTAINER + "/"
                + UiStoragePaths.uiPrefix(deployment.getApplicationId(), deployment.getName());
        return endpointName()
                .compose(endpoint -> getOrCreate(cdn.getRoutes().getAsync(resourceGroup, profileName, endpoint, label),
                                                 () -> cdn.getRoutes().createAsync(resourceGroup, profileName, endpoint, label, new RouteInner()
                                                         .withCustomDomains(List.of(new ActivatedResourceReference().withId(domain.id())))
                                                         .withOriginGroup(new ResourceReference().withId(originGroupId))
                                                         .withOriginPath(originPath)
                                                         .withRuleSets(List.of(new ResourceReference().withId(ruleSetId)))
                                                         .withSupportedProtocols(List.of(AfdEndpointProtocols.HTTP, AfdEndpointProtocols.HTTPS))
                                                         .withPatternsToMatch(List.of("/*"))
                                                         .withForwardingProtocol(ForwardingProtocol.HTTPS_ONLY)
                                                         .withLinkToDefaultDomain(LinkToDefaultDomain.DISABLED)
                                                         .withHttpsRedirect(HttpsRedirect.ENABLED)
                                                         .withCacheConfiguration(new AfdRouteCacheConfiguration()
                                                                 .withQueryStringCachingBehavior(AfdQueryStringCachingBehavior.IGNORE_QUERY_STRING)
                                                                 .withCompressionSettings(new CompressionSettings()
                                                                         .withIsCompressionEnabled(true)
                                                                         .withContentTypesToCompress(COMPRESSED_CONTENT_TYPES)))
                                                         .withEnabledState(EnabledState.ENABLED))))
                .mapEmpty();
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

    private static UiDeploymentStatus statusOf(AfdDomainInner domain) {
        DomainValidationState validation = domain.domainValidationState();
        UiDeploymentStatus ret;
        if (DomainValidationState.APPROVED.equals(validation) && DeploymentStatus.SUCCEEDED.equals(domain.deploymentStatus())) {
            ret = new UiDeploymentStatus(UiDeploymentStatusType.READY);
        } else if (validationLapsed(validation) || DomainValidationState.INTERNAL_ERROR.equals(validation)) {
            ret = new UiDeploymentStatus(UiDeploymentStatusType.FAILED, "Validation of " + domain.hostname() + " "
                    + validation + "; retry provisioning to validate again");
        } else {
            ret = new UiDeploymentStatus(UiDeploymentStatusType.PROVISIONING);
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
                 if (row == null || row.getStatus().type() != UiDeploymentStatusType.PROVISIONING) {
                     ret = Future.succeededFuture();
                 } else {
                     ret = checkProvisioning(row).compose(checked -> {
                         Future<Void> saved;
                         if (checked.getStatus().type() != UiDeploymentStatusType.PROVISIONING) {
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

    // Rule set names allow no dash
    private static String ruleSetName(Organization organization) {
        return "org" + organization.getId().replace("-", "");
    }

    private static void requireStorage(Organization organization) {
        Validate.notNull(organization, "organization is required");
        Validate.isTrue(organization.getStorage() != null && organization.getStorage().getBlobEndpoint() != null,
                        "Organization %s has no storage endpoint recorded", organization.getId());
    }

    private UiDeploymentProperties properties() {
        return kinoticProperties.getManagementApi().getUiDeployment();
    }

}
