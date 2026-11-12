package org.kinotic.domain.api.services;

import io.jsonwebtoken.JwtException;
import io.vertx.core.Future;
import org.kinotic.domain.api.model.license.EnterpriseFeature;
import org.kinotic.domain.api.model.license.License;

/**
 * The Enterprise Features the cluster's Kinotic Enterprise License entitles it to, decided at each call. Every node
 * runs on the same installed key: a key installed on one node reaches every node without a restart. A key bound to a
 * cluster turns its features on in that cluster alone.
 */
public interface EntitlementService {

    /** Whether the license turns {@code feature} on now. */
    boolean allows(EnterpriseFeature feature);

    /**
     * The license the cluster runs on, expired or not, with its status now, or {@code null} when no valid key is
     * installed.
     */
    License find();

    /**
     * Installs {@code key} as the license key of the whole cluster, in place of the one installed. The future
     * completes once the key is stored and this node runs on it; every other node is told of it then and runs on it
     * once it has read it, and a node the notice misses, or that could not read the key then, reads it at its next
     * reload, which it tries every ten minutes.
     *
     * @return the license the key holds; fails with a {@link JwtException} when the key is not a valid license key
     *         or its grace period has ended, and with an {@link IllegalArgumentException} when the key names no
     *         licensee or no expiry or is bound to another cluster, or when this cluster stores secrets in Azure Key
     *         Vault and the key does not turn {@link EnterpriseFeature#AZURE_KEY_VAULT} on
     */
    Future<License> install(String key);

    /**
     * The id of this cluster, the UID of its {@code kube-system} namespace, which a key bound to the cluster names;
     * {@code null} when the cluster does not run in Kubernetes, or has not been able to read the namespace yet.
     */
    String findClusterId();

    /**
     * Asks Minds Ignited's license server for the newest key of this cluster, signed in with the installed key, and
     * installs it as {@link #install(String)} does when it differs from the installed key. A key bound to no cluster
     * is not renewed.
     *
     * @return the license the cluster runs on afterwards, or {@code null} when no key bound to a cluster is installed;
     *         fails when the license server cannot be reached or refuses the installed key, or gives the reason no
     *         newer key comes, such as a past-due license
     */
    Future<License> renew();
}
