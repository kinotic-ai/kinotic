package org.kinotic.management.internal.api.services;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.PrivateEndpoint;
import com.azure.resourcemanager.network.models.PrivateLinkSubResourceName;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.BlobContainer;
import com.azure.resourcemanager.storage.models.MinimumTlsVersion;
import com.azure.resourcemanager.storage.models.PublicAccess;
import com.azure.resourcemanager.storage.models.StorageAccount;
import com.azure.resourcemanager.storage.models.StorageAccountSkuType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationStorageStatus;
import org.kinotic.domain.api.model.OrganizationStorageStatusType;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.management.api.config.OrganizationStorageProperties;
import org.kinotic.management.api.services.OrganizationStorageProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;

/**
 * Provisions one Azure storage account per organization: a locked-down StorageV2 account with
 * hierarchical namespace, holding the {@code ui} container, reachable from the platform network
 * through a private endpoint registered in the platform's private DNS zone. Provisioning runs
 * inside the deployment that first needs the storage and takes minutes; deployments of other
 * projects of the organization that arrive meanwhile wait for it rather than provisioning twice.
 * Every step is idempotent, so an organization left failed is provisioned again from wherever
 * it stopped.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.organizationStorage.disableProvisioner",
                       havingValue = "false", matchIfMissing = true)
public class AzureOrganizationStorageProvisioner implements OrganizationStorageProvisioner {

    /** Storage account names are 3 to 24 lowercase alphanumerics; this prefix plus 21 hex digits fills it. */
    private static final String ACCOUNT_NAME_PREFIX = "kin";
    private static final int ACCOUNT_NAME_HASH_LENGTH = 21;
    private static final long POLL_INTERVAL_MS = 5_000;
    /** A storage account plus a private endpoint create in a few minutes; past this a run in flight is presumed dead. */
    private static final long PROVISIONING_TIMEOUT_MS = 20 * 60_000;

    private final OrganizationService organizationService;
    private final Vertx vertx;
    private final OrganizationStorageProperties properties;
    private final TokenCredential credential;

    public AzureOrganizationStorageProvisioner(OrganizationService organizationService,
                                               Vertx vertx,
                                               KinoticManagementApiProperties kinoticProperties) {
        this.organizationService = organizationService;
        this.vertx = vertx;
        this.properties = kinoticProperties.getManagementApi().getOrganizationStorage();
        // On AKS this resolves to the kinotic-server workload identity, which holds the
        // storage and network roles on the resource group
        this.credential = new DefaultAzureCredentialBuilder().build();
    }

    @Override
    public Future<Organization> ensureStorage(String organizationId) {
        Validate.notBlank(organizationId, "organizationId is required");
        // Checked here rather than at startup, so a deployment that needs storage fails with
        // the missing setting named while a server that never publishes a UI runs unconfigured
        Validate.notEmpty(properties.getSubscriptionIds(), "kinotic.managementApi.organizationStorage.subscriptionIds is required");
        Validate.notBlank(properties.getResourceGroup(), "kinotic.managementApi.organizationStorage.resourceGroup is required");
        Validate.notBlank(properties.getLocation(), "kinotic.managementApi.organizationStorage.location is required");
        Validate.notBlank(properties.getPrivateEndpointSubnetId(), "kinotic.managementApi.organizationStorage.privateEndpointSubnetId is required");
        Validate.notBlank(properties.getPrivateDnsZoneId(), "kinotic.managementApi.organizationStorage.privateDnsZoneId is required");
        return organizationService.findById(organizationId)
                .compose(organization -> {
                    if (organization == null) {
                        throw new IllegalArgumentException("Organization not found: " + organizationId);
                    }
                    Future<Organization> ret;
                    OrganizationStorageStatusType status = organization.getStorageStatus() != null
                            ? organization.getStorageStatus().type() : null;
                    if (status == OrganizationStorageStatusType.READY) {
                        ret = Future.succeededFuture(organization);
                    } else if (status == OrganizationStorageStatusType.PROVISIONING && !stale(organization)) {
                        ret = awaitProvisioning(organizationId, System.currentTimeMillis() + PROVISIONING_TIMEOUT_MS);
                    } else {
                        ret = provision(organization);
                    }
                    return ret;
                });
    }

    // A run that died mid-provisioning leaves PROVISIONING behind; once it is older than any
    // run could take, the next caller provisions instead of waiting forever
    private static boolean stale(Organization organization) {
        Date updated = organization.getUpdated();
        return updated == null || updated.getTime() < System.currentTimeMillis() - PROVISIONING_TIMEOUT_MS;
    }

    private Future<Organization> awaitProvisioning(String organizationId, long deadline) {
        return vertx.timer(POLL_INTERVAL_MS)
                .compose(v -> organizationService.findById(organizationId))
                .compose(organization -> {
                    Future<Organization> ret;
                    OrganizationStorageStatusType status = organization != null && organization.getStorageStatus() != null
                            ? organization.getStorageStatus().type() : null;
                    if (status == OrganizationStorageStatusType.READY) {
                        ret = Future.succeededFuture(organization);
                    } else if (status == OrganizationStorageStatusType.FAILED) {
                        ret = Future.failedFuture(new IllegalStateException("Storage of organization " + organizationId
                                + " failed to provision: " + organization.getStorageStatus().message()));
                    } else if (System.currentTimeMillis() > deadline) {
                        ret = Future.failedFuture(new IllegalStateException("Storage of organization " + organizationId
                                + " is still provisioning after " + PROVISIONING_TIMEOUT_MS / 60_000 + " minutes"));
                    } else {
                        ret = awaitProvisioning(organizationId, deadline);
                    }
                    return ret;
                });
    }

    /**
     * Records the decisions (subscription, account name) and the PROVISIONING status before
     * touching Azure, so a retry after a failure targets the same account, then creates what is
     * missing in order: account, container, private endpoint with its DNS registration.
     */
    private Future<Organization> provision(Organization organization) {
        String organizationId = organization.getId();
        String subscriptionId = organization.getStorageSubscriptionId() != null
                ? organization.getStorageSubscriptionId()
                : chooseSubscription(organizationId);
        String accountName = accountName(organizationId);
        organization.setStorageSubscriptionId(subscriptionId)
                    .setStorageAccountName(accountName)
                    .setStorageStatus(new OrganizationStorageStatus(OrganizationStorageStatusType.PROVISIONING))
                    .setUpdated(new Date());
        AzureProfile profile = new AzureProfile(null, subscriptionId, AzureEnvironment.AZURE);
        StorageManager storage = StorageManager.authenticate(credential, profile);
        NetworkManager network = NetworkManager.authenticate(credential, profile);
        log.info("Provisioning storage account {} for organization {} in subscription {}",
                 accountName, organizationId, subscriptionId);
        return organizationService.saveSync(organization)
                .compose(saved -> bridge(ensureAccount(storage, accountName, organizationId)))
                .compose(account -> bridge(ensureContainer(storage, accountName))
                        .compose(container -> bridge(ensurePrivateEndpoint(network, account)))
                        .map(privateIp -> {
                            organization.setStorageBlobEndpoint(account.endPoints().primary().blob())
                                        .setStoragePrivateEndpointIp(privateIp)
                                        .setStorageStatus(new OrganizationStorageStatus(OrganizationStorageStatusType.READY))
                                        .setUpdated(new Date());
                            return organization;
                        }))
                .compose(organizationService::saveSync)
                .recover(error -> {
                    log.error("Storage provisioning for organization {} failed", organizationId, error);
                    organization.setStorageStatus(new OrganizationStorageStatus(OrganizationStorageStatusType.FAILED,
                                                                                error.getMessage()))
                                .setUpdated(new Date());
                    return organizationService.saveSync(organization)
                                              .compose(v -> Future.failedFuture(new IllegalStateException(
                                                      "Storage of organization " + organizationId
                                                              + " failed to provision: " + error.getMessage(), error)));
                });
    }

    private Mono<StorageAccount> ensureAccount(StorageManager storage, String accountName, String organizationId) {
        return storage.storageAccounts().getByResourceGroupAsync(properties.getResourceGroup(), accountName)
                      .onErrorResume(AzureOrganizationStorageProvisioner::isNotFound, error -> Mono.empty())
                      .switchIfEmpty(Mono.defer(() -> storage.storageAccounts()
                              .define(accountName)
                              .withRegion(properties.getLocation())
                              .withExistingResourceGroup(properties.getResourceGroup())
                              .withGeneralPurposeAccountKindV2()
                              .withSku(StorageAccountSkuType.STANDARD_LRS)
                              .withHnsEnabled(true)
                              .withMinimumTlsVersion(MinimumTlsVersion.TLS1_2)
                              .withOnlyHttpsTraffic()
                              .disableBlobPublicAccess()
                              // the private endpoint is the platform's way in; what reaches the
                              // account from outside is decided by the serving layer's origin rules
                              .withAccessFromSelectedNetworks()
                              .withAccessFromAzureServices()
                              .withTag("org", organizationId)
                              .createAsync()));
    }

    private Mono<BlobContainer> ensureContainer(StorageManager storage, String accountName) {
        return storage.blobContainers().getAsync(properties.getResourceGroup(), accountName, UI_CONTAINER)
                      .onErrorResume(AzureOrganizationStorageProvisioner::isNotFound, error -> Mono.empty())
                      .switchIfEmpty(Mono.defer(() -> storage.blobContainers()
                              .defineContainer(UI_CONTAINER)
                              .withExistingStorageAccount(properties.getResourceGroup(), accountName)
                              .withPublicAccess(PublicAccess.NONE)
                              .createAsync()));
    }

    /**
     * Places the account's blob private endpoint in the platform subnet and registers it in the
     * private DNS zone through a zone group, so the platform resolves the account's public name
     * to the private address. Emits that address.
     */
    private Mono<String> ensurePrivateEndpoint(NetworkManager network, StorageAccount account) {
        String endpointName = "pe-" + account.name();
        return network.privateEndpoints().getByResourceGroupAsync(properties.getResourceGroup(), endpointName)
                      .onErrorResume(AzureOrganizationStorageProvisioner::isNotFound, error -> Mono.empty())
                      .switchIfEmpty(Mono.defer(() -> network.privateEndpoints()
                              .define(endpointName)
                              .withRegion(properties.getLocation())
                              .withExistingResourceGroup(properties.getResourceGroup())
                              .withSubnetId(properties.getPrivateEndpointSubnetId())
                              .definePrivateLinkServiceConnection("blob")
                                  .withResourceId(account.id())
                                  .withSubResource(PrivateLinkSubResourceName.STORAGE_BLOB)
                                  .attach()
                              .createAsync()))
                      .flatMap(endpoint -> ensureDnsZoneGroup(endpoint).thenReturn(endpoint))
                      .flatMap(endpoint -> network.networkInterfaces()
                                                  .getByIdAsync(endpoint.networkInterfaces().getFirst().id())
                                                  .map(nic -> nic.primaryPrivateIP()));
    }

    // An endpoint carries at most one zone group, so any existing one is the registration
    private Mono<Void> ensureDnsZoneGroup(PrivateEndpoint endpoint) {
        return endpoint.privateDnsZoneGroups().listAsync().hasElements()
                       .flatMap(registered -> registered
                               ? Mono.empty()
                               : endpoint.privateDnsZoneGroups()
                                         .define("default")
                                         .withPrivateDnsZoneConfigure("blob", properties.getPrivateDnsZoneId())
                                         .createAsync()
                                         .then());
    }

    private <T> Future<T> bridge(Mono<T> mono) {
        return Future.fromCompletionStage(mono.toFuture(), vertx.getOrCreateContext());
    }

    private static boolean isNotFound(Throwable error) {
        return error instanceof ManagementException management
                && management.getResponse() != null
                && management.getResponse().getStatusCode() == 404;
    }

    /** Spreads organizations over the configured subscriptions deterministically, so a retry lands in the same one. */
    private String chooseSubscription(String organizationId) {
        return properties.getSubscriptionIds().get(Math.floorMod(organizationId.hashCode(), properties.getSubscriptionIds().size()));
    }

    /** {@code kin} plus the first 21 hex digits of the organization id's SHA-256: unique, and never a name a customer chose. */
    static String accountName(String organizationId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(organizationId.getBytes(StandardCharsets.UTF_8));
            return ACCOUNT_NAME_PREFIX + HexFormat.of().formatHex(digest).substring(0, ACCOUNT_NAME_HASH_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

}
