package org.kinotic.management.api.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration for the Elasticsearch index that backs each published {@code EntityDefinition}.
 * Bound under {@code kinotic.managementApi.entityDefinition.*} via {@link KinoticManagementApiProperties}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class EntityDefinitionProperties {

    /**
     * Primary shards for the Elasticsearch index backing a published {@code EntityDefinition}.
     */
    @Min(1)
    private int numberOfShards = 3;

    /**
     * Replicas of each primary shard for the index backing a published {@code EntityDefinition}.
     * An index costs {@code numberOfShards * (1 + numberOfReplicas)} against the cluster's shard
     * budget, and a replica stays unassigned until the cluster has another node to hold it.
     */
    @Min(0)
    private int numberOfReplicas = 2;

}
