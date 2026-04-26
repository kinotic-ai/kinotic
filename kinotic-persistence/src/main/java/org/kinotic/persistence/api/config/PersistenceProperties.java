package org.kinotic.persistence.api.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.Validate;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PersistenceProperties {

    private String structuresBaseUrl = "http://localhost";

    private final String indexPrefix = "kinotic_";

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
     * MCP server configuration
     */
    private Integer mcpPort = 3001;

    /**
     * Cluster eviction configuration
     */
    private ClusterEvictionProperties clusterEviction = new ClusterEvictionProperties();


    public boolean hasElasticUsernameAndPassword(){
        return elasticUsername != null && !elasticUsername.isBlank() && elasticPassword != null && !elasticPassword.isBlank();
    }

    public PersistenceProperties setOpenApiAdminPath(String path){
        Validate.notBlank(path, "openApiAdminPath must not be blank");
        if(path.endsWith("/")){
            this.openApiAdminPath = path;
        }else{
            this.openApiAdminPath = path + "/";
        }
        return this;
    }


    public PersistenceProperties setOpenApiPath(String path){
        Validate.notBlank(path, "openApiPath must not be blank");
        if(path.endsWith("/")){
            this.openApiPath = path;
        }else{
            this.openApiPath = path + "/";
        }
        return this;
    }

    public PersistenceProperties setGraphqlPath(String path) {
        Validate.notBlank(path, "graphqlPath must not be blank");
        if(path.endsWith("/")){
            this.graphqlPath = path;
        }else{
            this.graphqlPath = path + "/";
        }
        return this;
    }
}
