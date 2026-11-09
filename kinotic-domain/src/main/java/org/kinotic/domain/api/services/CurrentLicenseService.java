package org.kinotic.domain.api.services;

import io.jsonwebtoken.JwtException;
import io.vertx.core.Future;
import org.kinotic.domain.api.model.license.EnterpriseFeature;
import org.kinotic.domain.api.model.license.License;

/**
 * The Kinotic Enterprise License the cluster runs on, which decides at each call whether an Enterprise Feature is
 * on. Every node runs on the same installed key: a key installed on one node reaches every node without a restart.
 */
public interface CurrentLicenseService {

    /** Whether the license turns {@code feature} on now. */
    boolean allows(EnterpriseFeature feature);

    /**
     * The license the cluster runs on, expired or not, with its status now, or {@code null} when no valid key is
     * installed.
     */
    License find();

    /**
     * Installs {@code key} as the license key of the whole cluster, in place of the one installed. The future
     * completes once the key is stored and the nodes have been told of it; a node the notice misses, or that could
     * not read the key then, reads it at its next reload, which it tries every ten minutes.
     *
     * @return the license the key holds; fails with a {@link JwtException} when the key is not a valid license key
     *         or its grace period has ended, and with an {@link IllegalArgumentException} when the key names no
     *         licensee or no expiry, or when this cluster stores secrets in Azure Key Vault and the key does not turn
     *         {@link EnterpriseFeature#AZURE_KEY_VAULT} on
     */
    Future<License> install(String key);
}
