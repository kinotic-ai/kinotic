package org.kinotic.persistence.api.config;

import jakarta.validation.constraints.Min;
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

    /**
     * Most cached {@code EntityService} instances to keep, one per active {@code EntityDefinition}.
     * Past this the cache evicts and the next request reloads the definition from Elasticsearch.
     */
    @Min(1)
    private int entityServiceCacheMaxSize = 10_000;

    /**
     * Most cached {@code QueryExecutor} instances to keep, one per named query per
     * {@code EntityDefinition}. Past this the cache evicts and the next call rebuilds the executor.
     */
    @Min(1)
    private int namedQueriesCacheMaxSize = 10_000;

    /**
     * Cluster eviction configuration
     */
    private ClusterEvictionProperties clusterEviction = new ClusterEvictionProperties();

}
