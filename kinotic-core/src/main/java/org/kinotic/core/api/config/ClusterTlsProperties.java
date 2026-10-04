package org.kinotic.core.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Mutual TLS between the platform's nodes, on the clustered event bus and on Ignite's discovery and communication.
 * Each node presents the certificate in its key store and accepts a peer only when the peer's certificate chains to one
 * in the trust store, so only nodes holding a certificate the cluster trusts can join it or send messages to its nodes.
 * Both stores are PKCS12 files.
 */
@Getter
@Setter
@Accessors(chain = true)
public class ClusterTlsProperties {

    /**
     * Whether traffic between the platform's nodes uses mutual TLS. Every node of a cluster must agree.
     */
    private boolean enabled = false;

    /**
     * The PKCS12 file holding this node's private key and certificate.
     */
    private String keyStorePath;

    private String keyStorePassword;

    /**
     * The PKCS12 file holding the certificates, usually the issuing CA's, a peer's certificate must chain to.
     */
    private String trustStorePath;

    private String trustStorePassword;

    /**
     * @throws IllegalStateException when TLS is enabled but a store is not configured
     */
    public void requireStores() {
        if (enabled && (keyStorePath == null || keyStorePath.isBlank() || trustStorePath == null || trustStorePath.isBlank())) {
            throw new IllegalStateException("kinotic.clusterTls is enabled, so kinotic.clusterTls.keyStorePath and"
                                                    + " kinotic.clusterTls.trustStorePath must be set");
        }
    }
}
