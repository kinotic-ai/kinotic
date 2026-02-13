package org.kinotic.boot.internal.config;


import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.kinotic.boot.api.config.IgniteClusterDiscoveryType;
import org.kinotic.boot.api.config.IgniteProperties;
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
 * @see IgniteProperties
 * @see IgniteClusterDiscoveryType
 * @see TcpDiscoverySpi
 * @see TcpCommunicationSpi
 */
@Component
@Getter
@Setter
@Accessors(chain = true)
public class DefaultIgniteProperties implements IgniteProperties {

    private Integer communicationPort = TcpCommunicationSpi.DFLT_PORT;
    private Integer discoveryPort = TcpDiscoverySpi.DFLT_PORT;
    private IgniteClusterDiscoveryType discoveryType = IgniteClusterDiscoveryType.SHAREDFS;
    private Long joinTimeoutMs = TcpDiscoverySpi.DFLT_JOIN_TIMEOUT; // 0 seconds (no timeout)
    private String kubernetesAccountToken = null;
    private Boolean kubernetesIncludeNotReadyAddresses = false;
    private String kubernetesMasterUrl = null;
    private String kubernetesNamespace = "default";
    private String kubernetesServiceName = "structures";
    private String localAddress = null;
    private String localAddresses;
    private String sharedFsPath = "/tmp/structures-sharedfs";
    private String workDirectory = "/tmp/ignite";


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
