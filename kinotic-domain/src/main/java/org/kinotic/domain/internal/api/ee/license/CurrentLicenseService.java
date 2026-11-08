package org.kinotic.domain.internal.api.ee.license;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.kinotic.core.api.annotations.Consumer;
import org.kinotic.core.api.event.EventService;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.config.SecretStorageBackendType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

/**
 * The Kinotic Enterprise License the cluster runs on, which decides at each call whether an Enterprise Feature is
 * on. Every node runs on the same installed key: a key installed on one node reaches every node without a restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentLicenseService {

    // re-reads the installed key, so a node that missed a LicenseChangedEvent catches up, and notices expiry
    private static final long RELOAD_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long STORAGE_TIMEOUT_SECONDS = 30;

    private final InstalledLicenseKeyRepository repository;
    private final EventService eventService;
    private final Vertx vertx;
    private final KinoticDomainProperties properties;
    private final LicenseVerifier verifier = LicenseVerifier.trusted();

    private volatile License license;
    private String key = "";
    private LicenseStatus status;
    private long reloadTimer;
    // orders the reads of the installed key, so a read that finishes after a later one cannot apply an older key
    private final AtomicLong reads = new AtomicLong();
    private long appliedRead;

    @PostConstruct
    void start() {
        String installedKey;
        try {
            installedKey = repository.findInstalledKey()
                                     .toCompletionStage()
                                     .toCompletableFuture()
                                     .get(STORAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading the installed license key", e);
        } catch (Exception e) {
            throw new IllegalStateException("The installed license key could not be read", e);
        }
        apply(StringUtils.trimToEmpty(installedKey), reads.incrementAndGet());
        reloadTimer = vertx.setPeriodic(RELOAD_INTERVAL_MS, id -> reload());
    }

    @PreDestroy
    void stop() {
        vertx.cancelTimer(reloadTimer);
    }

    /** Whether the license turns {@code feature} on now. */
    public boolean allows(EnterpriseFeature feature) {
        License current = license;
        return current != null && current.allows(feature, Instant.now());
    }

    /** The license the cluster runs on, expired or not, or {@code null} when no valid key is installed. */
    public License find() {
        return license;
    }

    /**
     * Installs {@code key} as the license key of the whole cluster, in place of the one installed. The future
     * completes once the key is stored and the nodes have been told of it; a node the notice misses, or that could
     * not read the key then, reads it at its next reload, which it tries every ten minutes.
     *
     * @return the license the key holds
     * @throws JwtException             when the key is not a valid license key, or expired
     *                                  {@link License#GRACE_PERIOD} or longer ago
     * @throws IllegalArgumentException when the key names no licensee or no expiry, or when this cluster stores
     *                                  secrets in Azure Key Vault and the key does not turn
     *                                  {@link EnterpriseFeature#AZURE_KEY_VAULT} on
     */
    public Future<License> install(String key) {
        String trimmed = StringUtils.trimToEmpty(key);
        License installing = verifier.verify(trimmed);
        if (installing.status(Instant.now()) == LicenseStatus.EXPIRED) {
            throw new ExpiredJwtException(null, null, "The license key expired at " + installing.expiresAt()
                    + " and its grace period ended at " + installing.graceEndsAt());
        }
        if (properties.getDomain().getSecretStorage().getBackend() == SecretStorageBackendType.AZURE
                && !installing.allows(EnterpriseFeature.AZURE_KEY_VAULT, Instant.now())) {
            throw new IllegalArgumentException("This cluster stores secrets in Azure Key Vault, so its license key must turn on "
                                                       + EnterpriseFeature.AZURE_KEY_VAULT);
        }
        return repository.install(trimmed)
                         .compose(v -> {
                             // this node runs on the key it returns, whether or not its own notice is handled
                             apply(trimmed, reads.incrementAndGet());
                             return eventService.publishConfirmed(new LicenseChangedEvent())
                                                // a node the event missed, or whose read of the key failed, reads it on its next reload
                                                .recover(error -> {
                                                    log.warn("Not every node confirmed the new license key; each reads it at its next reload, every {} minutes",
                                                             TimeUnit.MILLISECONDS.toMinutes(RELOAD_INTERVAL_MS), error);
                                                    return Future.succeededFuture();
                                                });
                         })
                         .map(installing);
    }

    @Consumer
    public Future<Void> onLicenseChanged(LicenseChangedEvent event) {
        return readInstalledKey();
    }

    private void reload() {
        readInstalledKey().onFailure(error -> log.warn("The installed license key could not be read; the node keeps its license", error));
    }

    private Future<Void> readInstalledKey() {
        long read = reads.incrementAndGet();
        return repository.findInstalledKey().map(installed -> {
            apply(StringUtils.trimToEmpty(installed), read);
            return null;
        });
    }

    private synchronized void apply(String installedKey, long read) {
        if (read > appliedRead) {
            appliedRead = read;
            applyKey(installedKey);
        }
    }

    private void applyKey(String installedKey) {
        if (!Objects.equals(installedKey, key)) {
            key = installedKey;
            license = verifyOrNull(installedKey);
            status = null;
            if (license != null) {
                log.info("Kinotic Enterprise License for {}, expiring {}, turns on {}", license.licensee(), license.expiresAt(), license.features());
            } else if (installedKey.isEmpty()) {
                log.info("No Kinotic Enterprise License key is installed, so no Enterprise Feature is on");
            }
        }
        logStatusChange();
    }

    private void logStatusChange() {
        License current = license;
        LicenseStatus now = current != null ? current.status(Instant.now()) : null;
        if (now != status) {
            status = now;
            if (now == LicenseStatus.GRACE_PERIOD) {
                log.warn("The Kinotic Enterprise License for {} expired at {}; its Enterprise Features stay on until {}",
                         current.licensee(), current.expiresAt(), current.graceEndsAt());
            } else if (now == LicenseStatus.EXPIRED) {
                log.error("The Kinotic Enterprise License for {} expired at {} and its grace period ended at {}, so no Enterprise Feature is on",
                          current.licensee(), current.expiresAt(), current.graceEndsAt());
            }
        }
    }

    private License verifyOrNull(String key) {
        License ret = null;
        if (!key.isEmpty()) {
            try {
                ret = verifier.verify(key);
            } catch (JwtException | IllegalArgumentException e) {
                log.error("The installed Kinotic Enterprise License key is not valid: {}", e.getMessage());
            }
        }
        return ret;
    }
}
