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
import org.kinotic.core.api.annotations.KinoticConsumer;
import org.kinotic.core.api.event.EventDelivery;
import org.kinotic.core.api.event.EventService;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.config.SecretStorageBackendType;
import org.kinotic.domain.api.model.license.EnterpriseFeature;
import org.kinotic.domain.api.model.license.License;
import org.kinotic.domain.api.model.license.LicenseStatus;
import org.kinotic.domain.api.services.EntitlementService;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

/**
 * Runs every node on the license key installed in the cluster, reading it at startup, whenever a
 * {@link LicenseChangedEvent} announces a new one, and every ten minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultEntitlementService implements EntitlementService, SmartInitializingSingleton {

    // re-reads the installed key, so a node that missed a LicenseChangedEvent catches up, and notices expiry
    private static final long RELOAD_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long STORAGE_TIMEOUT_SECONDS = 30;

    private final InstalledLicenseKeyRepository repository;
    private final EventService eventService;
    private final Vertx vertx;
    private final KinoticDomainProperties properties;
    private final LicenseVerifier verifier = LicenseVerifier.trusted();

    // the license as verified, never handed out or changed; find() hands out a copy holding its status at the time
    private volatile License license;
    // the status last logged for the license, guarded by this
    private LicenseStatus loggedStatus;
    private String key = "";
    private long reloadTimer;
    // orders the reads of the installed key, so a read that finishes after a later one cannot apply an older key
    private final AtomicLong reads = new AtomicLong();
    private long appliedRead;

    // reads the key before the beans that check a feature initialize; a failed read leaves the node with no
    // Enterprise Feature until a later read succeeds, so a server never fails to start over its license
    @PostConstruct
    void start() {
        try {
            String installedKey = repository.findInstalledKey()
                                            .toCompletionStage()
                                            .toCompletableFuture()
                                            .get(STORAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            apply(StringUtils.trimToEmpty(installedKey), reads.incrementAndGet());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while reading the installed license key; no Enterprise Feature is on until it is read");
        } catch (Exception e) {
            log.error("The installed license key could not be read; no Enterprise Feature is on until it is read", e);
        }
        reloadTimer = vertx.setPeriodic(RELOAD_INTERVAL_MS, id -> reload());
    }

    // onLicenseChanged registers with the cluster after start() has read the key, so a key installed between the
    // two is announced to no consumer here; reading it again once every bean is initialized picks it up
    @Override
    public void afterSingletonsInstantiated() {
        reload();
    }

    @PreDestroy
    void stop() {
        vertx.cancelTimer(reloadTimer);
    }

    @Override
    public boolean allows(EnterpriseFeature feature) {
        License current = license;
        return current != null
                && statusAt(current, Instant.now()) != LicenseStatus.EXPIRED
                && current.getFeatures().contains(feature);
    }

    @Override
    public License find() {
        License current = license;
        License ret = null;
        if (current != null) {
            ret = new License().setLicensee(current.getLicensee())
                               .setIssuedAt(copyOf(current.getIssuedAt()))
                               .setExpiresAt(copyOf(current.getExpiresAt()))
                               .setGraceEndsAt(copyOf(current.getGraceEndsAt()))
                               .setStatus(statusAt(current, Instant.now()))
                               .setFeatures(Set.copyOf(current.getFeatures()));
        }
        return ret;
    }

    @Override
    public Future<License> install(String key) {
        String trimmed = StringUtils.trimToEmpty(key);
        License installing;
        try {
            installing = verifyInstallable(trimmed);
        } catch (JwtException | IllegalArgumentException e) {
            return Future.failedFuture(e);
        }
        return repository.install(trimmed)
                         .map(v -> {
                             // this node runs on the key it returns, whether or not its own notice is handled
                             apply(trimmed, reads.incrementAndGet());
                             // the caller is answered without waiting on the other nodes; a node the event misses, or
                             // whose read of the key fails, reads it on its next reload
                             eventService.publish(new LicenseChangedEvent())
                                         .onFailure(error -> log.warn("Not every node confirmed the new license key; each reads it at its next reload, every {} minutes",
                                                                      TimeUnit.MILLISECONDS.toMinutes(RELOAD_INTERVAL_MS), error));
                             return installing;
                         });
    }

    private License verifyInstallable(String key) {
        License ret = verifier.verify(key);
        ret.setStatus(statusAt(ret, Instant.now()));
        if (ret.getStatus() == LicenseStatus.EXPIRED) {
            throw new ExpiredJwtException(null, null, "The license key expired at " + ret.getExpiresAt().toInstant()
                    + " and its grace period ended at " + ret.getGraceEndsAt().toInstant());
        }
        if (properties.getDomain().getSecretStorage().getBackend() == SecretStorageBackendType.AZURE
                && !ret.getFeatures().contains(EnterpriseFeature.AZURE_KEY_VAULT)) {
            throw new IllegalArgumentException("This cluster stores secrets in Azure Key Vault, so its license key must turn on "
                                                       + EnterpriseFeature.AZURE_KEY_VAULT);
        }
        return ret;
    }

    @KinoticConsumer(delivery = EventDelivery.CONFIRMED)
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
            loggedStatus = null;
            // logStatusChange logs a key in its grace period or expired
            if (license != null && statusAt(license, Instant.now()) != LicenseStatus.EXPIRED) {
                log.info("Kinotic Enterprise License for {}, expiring {}, turns on {}",
                         license.getLicensee(), license.getExpiresAt().toInstant(), license.getFeatures());
            } else if (installedKey.isEmpty()) {
                log.info("No Kinotic Enterprise License key is installed, so no Enterprise Feature is on");
            }
        }
        logStatusChange();
    }

    // logs when the license enters its grace period or expires; runs at every read of the installed key, so a change
    // is logged within one reload interval
    private synchronized void logStatusChange() {
        License current = license;
        if (current != null) {
            LicenseStatus now = statusAt(current, Instant.now());
            if (now != loggedStatus) {
                loggedStatus = now;
                if (now == LicenseStatus.GRACE_PERIOD) {
                    log.warn("The Kinotic Enterprise License for {} expired at {}; its Enterprise Features stay on until {}",
                             current.getLicensee(), current.getExpiresAt().toInstant(), current.getGraceEndsAt().toInstant());
                } else if (now == LicenseStatus.EXPIRED) {
                    log.error("The Kinotic Enterprise License for {} expired at {} and its grace period ended at {}, so no Enterprise Feature is on",
                              current.getLicensee(), current.getExpiresAt().toInstant(), current.getGraceEndsAt().toInstant());
                }
            }
        }
    }

    private static Date copyOf(Date date) {
        return date != null ? new Date(date.getTime()) : null;
    }

    private static LicenseStatus statusAt(License license, Instant now) {
        LicenseStatus ret;
        if (now.isBefore(license.getExpiresAt().toInstant())) {
            ret = LicenseStatus.ACTIVE;
        } else if (now.isBefore(license.getGraceEndsAt().toInstant())) {
            ret = LicenseStatus.GRACE_PERIOD;
        } else {
            ret = LicenseStatus.EXPIRED;
        }
        return ret;
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
