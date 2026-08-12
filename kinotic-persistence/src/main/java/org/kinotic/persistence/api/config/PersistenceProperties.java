package org.kinotic.persistence.api.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PersistenceProperties {

    private final String indexPrefix = "kinotic_";

    @NotNull
    private String tenantIdFieldName = "tenantId";

    /**
     * Cluster eviction configuration
     */
    private ClusterEvictionProperties clusterEviction = new ClusterEvictionProperties();

}
