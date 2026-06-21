package org.kinotic.persistence.api.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.Validate;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PersistenceProperties {

    private String structuresBaseUrl = "http://localhost";

    private final String indexPrefix = "kinotic_";

    @NotNull
    private String tenantIdFieldName = "tenantId";

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
