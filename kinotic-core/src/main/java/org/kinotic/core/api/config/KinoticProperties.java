package org.kinotic.core.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 *
 * Created by Navíd Mitchell 🤪on 1/18/21
 */
@Component
@ConfigurationProperties(prefix = "kinotic")
@Primary
@Validated
@Getter
@Setter
@Accessors(chain = true)
public class KinoticProperties {

    /**
     * If true, additional information will be provided to clients,
     * including server information and information about errors occurring when invoking services
     * This is off by default since this could reveal server implementation details
     */
    private boolean debug = false;

    /**
     * Sets the host. Defaults to null.
     * When the clustered eventbus starts, it tries to bind to the provided host.
     * If the host is null, then it tries to bind to the same host as the underlying cluster manager.
     * As a last resort, an address will be picked among the available network interfaces.
     */
    private String eventBusClusterHost = null;

    /**
     * The port to use when clustering = 0 (meaning assign a random port)
     */
    private int eventBusClusterPort = 0;

    /**
     * The public-facing hostname to be used for clustering.
     * Sometimes, e.g., when running on certain clouds, the local address the server listens on for clustering is different from the address that other nodes connect to it at, as the OS / cloud infrastructure does some kind of proxying.
     * If this is the case, you can specify a public hostname which is different from the hostname the server listens on.
     * The default value is null, which means use the same as the cluster hostname.
     */
    private String eventBusClusterPublicHost = null;

    /**
     * The cluster public port or -1 which means use the same as the cluster port.
     */
    private int eventBusClusterPublicPort = -1;

    /**
     * Ignite properties for configuring Apache Ignite
     */
    private IgniteProperties ignite = new IgniteProperties();

    /**
     * The largest event payload the platform accepts, in bytes. It bounds the STOMP body, the
     * WebSocket frame carrying it, and the JSON document the invoker will parse. A frame's payload is
     * allocated off heap in full as it arrives, so this also sets how much direct memory a single
     * connection can demand and must be raised together with {@code -XX:MaxDirectMemorySize}.
     * NOTE: {@code -XX:MaxDirectMemorySize} will need to be maxEventPayloadSize x (maxNumberOfConnections sending frames at this size)
     */
    private int maxEventPayloadSize = 1024 * 1024 * 2; // 2MB

    /**
     * The maximum number of CPU cores if not set or less than 1, this will default to the available number of cores.
     */
    private int maxNumberOfCoresToUse = Math.max(Runtime.getRuntime().availableProcessors(), 1);

    /**
     * The maximum amount of off-heap memory to use for Ignite cache storage.
     */
    private long maxOffHeapMemory = DataStorageConfiguration.DFLT_DATA_REGION_MAX_SIZE;

    /**
     * Paths to platform-level secret files (JWT signing keys, secret-storage master keys).
     * Files are mounted into the pod by the Azure Key Vault CSI driver in production or
     * by a Kubernetes Secret volume locally, and watched for changes so rotation flows
     * through without a restart.
     */
    private PlatformSecretsProperties platformSecrets = new PlatformSecretsProperties();


    public void setMaxNumberOfCoresToUse(int maxNumberOfCoresToUse) {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        this.maxNumberOfCoresToUse = maxNumberOfCoresToUse > 0 ? Math.min(availableProcessors, maxNumberOfCoresToUse) : Math.max(availableProcessors, 1);
    }

}
