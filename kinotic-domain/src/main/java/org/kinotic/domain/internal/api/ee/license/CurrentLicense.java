package org.kinotic.domain.internal.api.ee.license;

import io.jsonwebtoken.JwtException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.kinotic.core.api.event.ClusterEventPublisher;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * The Kinotic Enterprise License the cluster runs on, which decides at each call whether an Enterprise Feature is
 * on. Every node runs on the same installed key: a key installed on one node reaches every node without a restart.
 * When no key is installed, or the {@value #LICENSE_KEY_PROPERTY} property holds a key issued later than the one
 * installed, the property's key is installed as the node starts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentLicense {

    /** The property holding a license key the server installs as it starts, when it is newer than the installed key. */
    public static final String LICENSE_KEY_PROPERTY = "kinotic.ee.licenseKey";

    // re-reads the installed key, so a node that missed a LicenseChangedEvent catches up, and notices expiry
    private static final long RELOAD_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long STORAGE_TIMEOUT_SECONDS = 30;

    private final InstalledLicenseKeyRepository repository;
    private final ClusterEventPublisher clusterEventPublisher;
    private final Vertx vertx;
    private final KinoticDomainProperties properties;
    private final LicenseVerifier verifier = LicenseVerifier.trusted();

    private volatile License license;
    private String key = "";
    private LicenseStatus status;
    private long reloadTimer;

    @PostConstruct
    void start() {
        String propertyKey = StringUtils.trimToEmpty(properties.getEe().getLicenseKey());
        String installedKey = StringUtils.trimToEmpty(await(repository.findInstalledKey()));
        License fromProperty = verifyOrNull(propertyKey, LICENSE_KEY_PROPERTY);
        License installed = verifyOrNull(installedKey, "the installed license key");
        String startKey;
        if (fromProperty != null && (installed == null || fromProperty.isNewerThan(installed))) {
            await(repository.install(propertyKey));
            log.info("Installed the Kinotic Enterprise License key in {}, which is newer than the installed key", LICENSE_KEY_PROPERTY);
            startKey = propertyKey;
        } else {
            startKey = installedKey;
        }
        apply(startKey);
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

    /** The license the cluster runs on, or {@code null} when no valid key is installed. */
    public License find() {
        return license;
    }

    /**
     * Installs {@code key} as the license key of the whole cluster, in place of the one installed, and completes once
     * every node runs on it.
     *
     * @return the license the key holds
     * @throws JwtException             when the key is not a valid license key, or expired
     *                                  {@link License#GRACE_PERIOD} or longer ago
     * @throws IllegalArgumentException when the key names no licensee or no expiry
     */
    public Future<License> install(String key) {
        String trimmed = StringUtils.trimToEmpty(key);
        License installing = verifier.verify(trimmed);
        return repository.install(trimmed)
                         .compose(v -> clusterEventPublisher.publish(new LicenseChangedEvent())
                                                            // a node the event missed reads the key on its next reload
                                                            .recover(error -> {
                                                                log.warn("Not every node confirmed the new license key; each reads it within {} minutes",
                                                                         TimeUnit.MILLISECONDS.toMinutes(RELOAD_INTERVAL_MS), error);
                                                                return Future.succeededFuture();
                                                            }))
                         .map(installing);
    }

    @EventListener
    public void onLicenseChanged(LicenseChangedEvent event) {
        // runs on an Ignite compute thread, which waits so the publisher's completion means the node has the key
        apply(StringUtils.trimToEmpty(await(repository.findInstalledKey())));
    }

    private void reload() {
        repository.findInstalledKey()
                  .onSuccess(installed -> apply(StringUtils.trimToEmpty(installed)))
                  .onFailure(error -> log.warn("The installed license key could not be read; the node keeps its license", error));
    }

    private synchronized void apply(String installedKey) {
        if (!Objects.equals(installedKey, key)) {
            key = installedKey;
            license = verifyOrNull(installedKey, "the installed license key");
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

    private License verifyOrNull(String key, String source) {
        License ret = null;
        if (!key.isEmpty()) {
            try {
                ret = verifier.verify(key);
            } catch (JwtException | IllegalArgumentException e) {
                log.error("The Kinotic Enterprise License key in {} is not valid: {}", source, e.getMessage());
            }
        }
        return ret;
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.toCompletionStage().toCompletableFuture().get(STORAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading the installed license key", e);
        } catch (Exception e) {
            throw new IllegalStateException("The installed license key could not be read or written", e);
        }
    }
}
