package org.kinotic.structures.api.config;

import org.kinotic.structures.api.config.ClusterDiscoveryType;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "structures")
public class StructuresProperties {

    private String structuresBaseUrl = "http://localhost";

    private final String indexPrefix = "struct_";

    @NotNull
    private String tenantIdFieldName = "tenantId";

    @NotNull
    private Duration elasticConnectionTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration elasticSocketTimeout = Duration.ofMinutes(1);

    /**
     * The interval to check the health of the elastic cluster
     */
    @NotNull
    private Duration elasticHealthCheckInterval = Duration.ofMinutes(1);

    @NotNull
    private List<ElasticConnectionInfo> elasticConnections = List.of(new ElasticConnectionInfo());

    private String elasticUsername = null;

    private String elasticPassword = null;

    /**
     * The allowed origin pattern for CORS
     * Defaults to "http://localhost.*"
     * If you want to allow all origins use "*"
     * Internally uses Java Regex Patterns to match
     * @see java.util.regex.Pattern
     */
    private String corsAllowedOriginPattern = "http://localhost.*";

    /**
     * The allowed headers for CORS
     */
    private Set<String> corsAllowedHeaders = Set.of("Accept", "Authorization", "Content-Type");

    /**
     * If set will set the CORS Access-Control-Allow-Credentials header to this value
     * If true then allowed origins must not contain a wildcard "*"
     */
    private Boolean corsAllowCredentials = null;

    /**
     * The max length of all HTTP headers in bytes. Default is 8KB.
     */
    private int maxHttpHeaderSize = 1024 * 8; // 8KB

    /**
     * The max length of the HTTP body in bytes, -1 for no limit. Default is no limit.
     */
    private long maxHttpBodySize = -1; // No Limit

    private OpenApiSecurityType openApiSecurityType = OpenApiSecurityType.NONE;

    private int openApiPort = 8080;

    private String openApiPath = "/api/";

    private String openApiAdminPath = "/admin/api/";

    private int graphqlPort = 4000;

    private String graphqlPath = "/graphql/";

    /**
     * The port that the static files and the health check will be served from
     */
    private int webServerPort = 9090;

    /**
     * The path that the health check will be served from
     */
    private String healthCheckPath = "/health/";

    /**
     * If true, static files are served from resources/webroot
     */
    private boolean enableStaticFileServer = false;

    /**
     * If true will initialize the Structures with sample data
     */
    private boolean initializeWithSampleData = false;

    /**
     * Root path for blob storage of insights data.
     * Defaults to /tmp/blobs
     */
    private String blobStoreRoot = "/tmp/blobs";

    /**
     * MCP server configuration
     */
    private Integer mcpPort = 3001;

    /**
     * The maximum number of retry attempts for cluster sync
     */
    private Integer maxClusterSyncRetryAttempts = 3;

    /**
     * The delay between retry attempts for cluster sync
     */
    private Long clusterSyncRetryDelayMs = 1000L; // 1 second

    /**
     * The timeout for cluster sync
     */
    private Long clusterSyncTimeoutMs = 30000L; // 30 seconds
    
    // ========== Apache Ignite Cluster Configuration ==========
    
    /**
     * Cluster discovery type for Apache Ignite.
     * Valid values: LOCAL, SHAREDFS, KUBERNETES
     * - LOCAL: Single-node, no clustering (default for development)
     * - SHAREDFS: Shared filesystem discovery (Docker/VM environments)
     * - KUBERNETES: Kubernetes discovery via K8s API
     */
    private ClusterDiscoveryType clusterDiscoveryType = ClusterDiscoveryType.LOCAL;
    
    /**
     * Comma-separated list of addresses for shared filesystem discovery.
     * Format: "host1:port1,host2:port2,host3:port3"
     * Only used when clusterDiscoveryType = "sharedfs"
     * Example: "node1:47500,node2:47500,node3:47500"
     */
    private String clusterSharedFsPath = "/tmp/structures-sharedfs";
    
    /**
     * Kubernetes namespace where Structures pods are deployed.
     * Only used when clusterDiscoveryType = "kubernetes"
     */
    private String clusterKubernetesNamespace = "default";
    
    /**
     * Kubernetes service name for Ignite discovery (headless service).
     * Only used when clusterDiscoveryType = "kubernetes"
     */
    private String clusterKubernetesServiceName = "structures";
    
    /**
     * Kubernetes master URL for API access.
     * If null, uses in-cluster configuration.
     * Only used when clusterDiscoveryType = "kubernetes"
     */
    private String clusterKubernetesMasterUrl = null;
    
    /**
     * Kubernetes account token for API authentication.
     * If null, uses service account token from mounted secret.
     * Only used when clusterDiscoveryType = "kubernetes"
     */
    private String clusterKubernetesAccountToken = null;
    
    /**
     * Port used for Ignite discovery protocol
     */
    private Integer clusterDiscoveryPort = 47500;
    
    /**
     * Port used for Ignite node communication
     */
    private Integer clusterCommunicationPort = 47100;
    
    /**
     * Timeout in milliseconds for cluster formation/join
     */
    private Long clusterJoinTimeoutMs = 30000L; // 30 seconds

    public boolean hasElasticUsernameAndPassword(){
        return elasticUsername != null && !elasticUsername.isBlank() && elasticPassword != null && !elasticPassword.isBlank();
    }

    public StructuresProperties setOpenApiAdminPath(String path){
        Validate.notBlank(path, "openApiAdminPath must not be blank");
        if(path.endsWith("/")){
            this.openApiAdminPath = path;
        }else{
            this.openApiAdminPath = path + "/";
        }
        return this;
    }


    public StructuresProperties setOpenApiPath(String path){
        Validate.notBlank(path, "openApiPath must not be blank");
        if(path.endsWith("/")){
            this.openApiPath = path;
        }else{
            this.openApiPath = path + "/";
        }
        return this;
    }

    public StructuresProperties setGraphqlPath(String path) {
        Validate.notBlank(path, "graphqlPath must not be blank");
        if(path.endsWith("/")){
            this.graphqlPath = path;
        }else{
            this.graphqlPath = path + "/";
        }
        return this;
    }
}
