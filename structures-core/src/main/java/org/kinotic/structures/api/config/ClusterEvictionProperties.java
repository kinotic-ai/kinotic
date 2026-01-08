package org.kinotic.structures.api.config;

import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Ignite Cluster Configuration Properties
 * 
 * @author Navid Mitchell
 * @since 1.0.0
 * @version 1.0.0
 * @see StructuresProperties
 * @see ClusterDiscoveryType
 * @see ClusterDiscoveryType#LOCAL
 * @see ClusterDiscoveryType#SHAREDFS
 * @see ClusterDiscoveryType#KUBERNETES
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ClusterEvictionProperties {
    
    /**
     * The maximum number of retry attempts for cluster cache sync
     */
    private Integer maxCacheSyncRetryAttempts = 3;

    /**
     * The delay between retry attempts for cluster cache sync
     */
    private Long cacheSyncRetryDelayMs = 1000L; // 1 second

    /**
     * The timeout for cluster cache sync
     */
    private Long cacheSyncTimeoutMs = 30000L; // 30 seconds
    
    // ========== Apache Ignite Cluster Configuration ==========
    
 

    // /**
    //  * Port used for Ignite JMX
    //  */
    // private Integer jmxPort = 49112;

    // /**
    //  * Port used for Ignite thin client/JDBC/ODBC
    //  */
    // private Integer thinClientPort = 10800;

    // /**
    //  * Port used for Ignite REST API
    //  */
    // private Integer restApiPort = 8080;

    // /**
    //  * Port used for Ignite control script
    //  */
    // private Integer controlScriptPort = 11211;
    
}
