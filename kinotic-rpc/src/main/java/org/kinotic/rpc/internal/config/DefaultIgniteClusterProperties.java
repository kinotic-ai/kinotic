package org.kinotic.rpc.internal.config;


import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.kinotic.continuum.api.config.IgniteClusterDiscoveryType;
import org.kinotic.continuum.api.config.IgniteClusterProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Properties for the Ignite cluster configuration
 * @author Nic Padilla
 * @since 1.0.0
 * @version 1.0.0
 * @see IgniteClusterProperties
 * @see IgniteClusterDiscoveryType
 * @see TcpDiscoverySpi
 * @see TcpCommunicationSpi
 */
@Component
@ConfigurationProperties(prefix = "continuum.cluster")
@Getter
@Setter
@Accessors(chain = true)
public class DefaultIgniteClusterProperties implements IgniteClusterProperties {

    private IgniteClusterDiscoveryType discoveryType = IgniteClusterDiscoveryType.SHAREDFS;
    private Long joinTimeoutMs = TcpDiscoverySpi.DFLT_JOIN_TIMEOUT; // 0 seconds (no timeout)
    private String localAddress = null;
    private Integer discoveryPort = TcpDiscoverySpi.DFLT_PORT;
    private Integer communicationPort = TcpCommunicationSpi.DFLT_PORT;
    private String localAddresses;
    private String sharedFsPath = "/tmp/structures-sharedfs";
    private String kubernetesNamespace = "default";
    private String kubernetesServiceName = "structures";
    private Boolean kubernetesIncludeNotReadyAddresses = false;
    private String kubernetesMasterUrl = null;
    private String kubernetesAccountToken = null;


    @Override
    public String toString() {
        ToStringBuilder sb = new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                                    .append("discoveryType", discoveryType)
                                    .append("localAddress", localAddress)
                                    .append("joinTimeoutMs", joinTimeoutMs)
                                    .append("discoveryPort", discoveryPort)
                                    .append("communicationPort", communicationPort);

        if (discoveryType == IgniteClusterDiscoveryType.SHAREDFS) {
            sb.append("sharedFsPath", sharedFsPath);
        }else if (discoveryType == IgniteClusterDiscoveryType.KUBERNETES) {
            sb.append("kubernetesNamespace", kubernetesNamespace)
              .append("kubernetesServiceName", kubernetesServiceName)
              .append("kubernetesIncludeNotReadyAddresses", kubernetesIncludeNotReadyAddresses);
        }else if (discoveryType == IgniteClusterDiscoveryType.LOCAL) {
            sb.append("localAddresses", localAddresses);
        }

        return sb.toString();
    }
}
