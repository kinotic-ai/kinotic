package org.kinotic.queue.internal.cluster;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.ignite.Ignite;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.internal.processors.cache.GridCacheAffinityManager;
import org.apache.ignite.util.AttributeNodeFilter;
import org.kinotic.queue.api.config.KinoticQueueProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which queue nodes hold the copies of each shard. Every node computes the same placement for the same
 * cluster topology: the first node of a shard's copies owns it, the others follow it. A placement once looked up is
 * remembered until the topology changes; a lookup may block while the cluster changes topology.
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
    // Prefixes the node id standing in for the storage id of a placed node that left
    private static final String UNREACHABLE_PREFIX = "?";

    private final Ignite ignite;
    @Getter
    @Accessors(fluent = true)
    private final int replicationFactor;
    // Gives the placement cache's affinity version, which changes whenever the placement of any shard may change,
    // including when a node that joined takes over shards once the cluster rebalanced
    private final GridCacheAffinityManager affinity;
    private volatile PlacementSnapshot snapshot;

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
        this.affinity = ((IgniteEx) ignite).cachex(PLACEMENT_CACHE).context().affinity();
        this.snapshot = takeSnapshot(affinity.affinityTopologyVersion(), 0);
    }

    /**
     * @return the configuration of a cache every queue node holds in full, and no other node holds: an atomic cache
     * whose writes complete once every queue node has them
     */
    public static <V> CacheConfiguration<String, V> queueNodeCache(String name) {
        CacheConfiguration<String, V> ret = new CacheConfiguration<>(name);
        ret.setCacheMode(CacheMode.REPLICATED);
        ret.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        ret.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
        ret.setNodeFilter(new AttributeNodeFilter(QUEUE_NODE_ATTRIBUTE, Boolean.TRUE));
        return ret;
    }

    /**
     * @return the ids of the nodes holding the shard's copies, owner first; fewer than the replication factor when
     * the cluster has fewer queue nodes
     */
    public List<String> replicas(String queue, int shard) {
        return snapshot().replicas().computeIfAbsent(queue + "/" + shard, key -> ignite.affinity(PLACEMENT_CACHE)
                                                                                     .mapKeyToPrimaryAndBackups(key)
                                                                                     .stream()
                                                                                     .map(node -> node.id().toString())
                                                                                     .toList());
    }

    /**
     * @return the shard's {@link #replicas}, without blocking, when they were looked up since the topology last
     * changed; null otherwise
     */
    public List<String> findKnownReplicas(String queue, int shard) {
        return snapshot().replicas().get(queue + "/" + shard);
    }

    /**
     * @return the ids of every node running the queue module
     */
    public List<String> queueNodes() {
        return snapshot().queueNodes();
    }

    /**
     * @return the storage id of the node's data, or null when the node is no longer in the cluster
     */
    public String storageId(String nodeId) {
        return snapshot().storageIds().get(nodeId);
    }

    /**
     * @param replicas the ids of the nodes holding a shard's copies, as {@link #replicas} returns them
     * @return the storage id of each of those nodes other than this one, by node id, in placement order; a node that
     * left keeps its place under a storage id no copy has, which {@link #isUnreachable} recognizes
     */
    public Map<String, String> followers(List<String> replicas) {
        String self = localNodeId();
        Map<String, String> ret = new LinkedHashMap<>();
        for (String node : replicas) {
            if (!node.equals(self)) {
                String storageId = storageId(node);
                ret.put(node, storageId != null ? storageId : UNREACHABLE_PREFIX + node);
            }
        }
        return ret;
    }

    /**
     * @return whether the storage id stands in for a placed node that left, whose copy can never answer
     */
    public static boolean isUnreachable(String storageId) {
        return storageId.startsWith(UNREACHABLE_PREFIX);
    }

    /**
     * @return the id of the queue node whose data has the storage id, or null when no such node is in the cluster
     */
    public String nodeWithStorageId(String storageId) {
        return snapshot().nodesByStorageId().get(storageId);
    }

    /**
     * @return a number that changes whenever the placement of any shard, or the cluster's queue nodes, may have changed
     */
    public long version() {
        return snapshot().number();
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

    // The snapshot of the current affinity version, taken by the first lookup that finds the version changed
    private PlacementSnapshot snapshot() {
        PlacementSnapshot ret = snapshot;
        AffinityTopologyVersion current = affinity.affinityTopologyVersion();
        if (!ret.affinityVersion().equals(current)) {
            ret = takeSnapshot(current, ret.number() + 1);
            snapshot = ret;
        }
        return ret;
    }

    private PlacementSnapshot takeSnapshot(AffinityTopologyVersion affinityVersion, long number) {
        List<String> queueNodes = new ArrayList<>();
        Map<String, String> storageIds = new HashMap<>();
        Map<String, String> nodesByStorageId = new HashMap<>();
        for (ClusterNode node : ignite.cluster().nodes()) {
            String nodeId = node.id().toString();
            String storageId = node.attribute(STORAGE_ID_ATTRIBUTE);
            if (Boolean.TRUE.equals(node.attribute(QUEUE_NODE_ATTRIBUTE))) {
                queueNodes.add(nodeId);
            }
            if (storageId != null) {
                storageIds.put(nodeId, storageId);
                nodesByStorageId.putIfAbsent(storageId, nodeId);
            }
        }
        return new PlacementSnapshot(affinityVersion, number, new ConcurrentHashMap<>(), List.copyOf(queueNodes), storageIds, nodesByStorageId);
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
