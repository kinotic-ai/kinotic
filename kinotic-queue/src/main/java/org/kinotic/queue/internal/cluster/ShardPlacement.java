package org.kinotic.queue.internal.cluster;

import org.apache.ignite.Ignite;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.util.AttributeNodeFilter;
import org.kinotic.queue.api.config.KinoticQueueProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Decides which queue nodes hold the copies of each shard. Every node computes the same placement for the same
 * cluster topology: the first node of a shard's copies owns it, the others follow it.
 */
@Component
public class ShardPlacement {

    /**
     * The Ignite node attribute that marks a node running the queue module.
     */
    public static final String QUEUE_NODE_ATTRIBUTE = "kinotic.queue.node";

    // Holds no data; its affinity function assigns shard keys to queue nodes
    private static final String PLACEMENT_CACHE = "kinotic_queue_placement";

    private final Ignite ignite;
    private final int replicationFactor;

    public ShardPlacement(Ignite ignite, KinoticQueueProperties properties) {
        this.ignite = ignite;
        this.replicationFactor = properties.getQueue().getReplicationFactor();
        CacheConfiguration<String, Boolean> configuration = new CacheConfiguration<>(PLACEMENT_CACHE);
        configuration.setCacheMode(CacheMode.PARTITIONED);
        configuration.setBackups(replicationFactor - 1);
        configuration.setNodeFilter(new AttributeNodeFilter(QUEUE_NODE_ATTRIBUTE, Boolean.TRUE));
        ignite.getOrCreateCache(configuration);
    }

    /**
     * @return the ids of the nodes holding the shard's copies, owner first; fewer than the replication factor when
     * the cluster has fewer queue nodes
     */
    public List<String> replicas(String queue, int shard) {
        return ignite.affinity(PLACEMENT_CACHE)
                     .mapKeyToPrimaryAndBackups(queue + "/" + shard)
                     .stream()
                     .map(node -> node.id().toString())
                     .toList();
    }

    /**
     * @return the ids of every node running the queue module
     */
    public List<String> queueNodes() {
        return ignite.cluster()
                     .forAttribute(QUEUE_NODE_ATTRIBUTE, Boolean.TRUE)
                     .nodes()
                     .stream()
                     .map(ClusterNode::id)
                     .map(Object::toString)
                     .toList();
    }

    public String localNodeId() {
        return ignite.cluster().localNode().id().toString();
    }

    /**
     * @return the cluster topology version, which increases every time a node joins or leaves
     */
    public long topologyVersion() {
        return ignite.cluster().topologyVersion();
    }

    public int replicationFactor() {
        return replicationFactor;
    }

    /**
     * @return how many copies of a shard make a majority of the replication factor
     */
    public int quorum() {
        return replicationFactor / 2 + 1;
    }
}
