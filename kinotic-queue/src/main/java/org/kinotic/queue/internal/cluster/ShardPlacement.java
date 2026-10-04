package org.kinotic.queue.internal.cluster;

import org.apache.ignite.Ignite;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.util.AttributeNodeFilter;
import org.kinotic.queue.api.config.KinoticQueueProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Decides which queue nodes hold the copies of each shard. Every node computes the same placement for the same
 * cluster topology: the first node of a shard's copies owns it, the others follow it. Lookups may block while the
 * cluster changes topology.
 */
@Component
public class ShardPlacement {

    /**
     * The Ignite node attribute that marks a node running the queue module.
     */
    public static final String QUEUE_NODE_ATTRIBUTE = "kinotic.queue.node";

    /**
     * The Ignite node attribute that holds the {@link org.kinotic.queue.internal.log.QueueLog#storageId storage id}
     * of a queue node's data.
     */
    public static final String STORAGE_ID_ATTRIBUTE = "kinotic.queue.storageId";

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
        int clusterBackups = ignite.getOrCreateCache(configuration).getConfiguration(CacheConfiguration.class).getBackups();
        // The first queue node to start creates the cache, so its replication factor is the cluster's; a node that
        // disagreed would count majorities differently from the others
        if (clusterBackups != replicationFactor - 1) {
            throw new IllegalStateException("kinotic.queue.replicationFactor is " + replicationFactor + " on this node but "
                                                    + (clusterBackups + 1) + " on the queue nodes already running");
        }
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

    /**
     * @return the storage id of the node's data, or null when the node is no longer in the cluster
     */
    public String storageId(String nodeId) {
        ClusterNode node = ignite.cluster().node(UUID.fromString(nodeId));
        return node != null ? node.attribute(STORAGE_ID_ATTRIBUTE) : null;
    }

    /**
     * @return the id of the queue node whose data has the storage id, or null when no such node is in the cluster
     */
    public String nodeWithStorageId(String storageId) {
        return ignite.cluster()
                     .forAttribute(STORAGE_ID_ATTRIBUTE, storageId)
                     .nodes()
                     .stream()
                     .findFirst()
                     .map(node -> node.id().toString())
                     .orElse(null);
    }

    public String localNodeId() {
        return ignite.cluster().localNode().id().toString();
    }

    /**
     * @return the order in which this node joined the cluster, which no other node in the cluster shares
     */
    public long localNodeOrder() {
        return ignite.cluster().localNode().order();
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

    /**
     * @return how many copies of a set of {@code copies} copies make a majority of it; a set smaller than the
     * replication factor counts as that large, its missing copies holding nothing
     */
    public int quorum(int copies) {
        return Math.max(replicationFactor, copies) / 2 + 1;
    }
}
