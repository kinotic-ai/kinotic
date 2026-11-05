package org.kinotic.queue.internal.cluster;

import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The placement {@link ShardPlacement} computed for one affinity version of its placement cache.
 *
 * @param affinityVersion  the affinity version, which changes whenever the placement of any shard may change
 * @param number           grows with every snapshot taken
 * @param replicas         the ids of the nodes holding each shard's copies, owner first, by "queue/shard"; filled as
 *                         shards are looked up
 * @param queueNodes       the ids of the nodes running the queue module
 * @param storageIds       the storage id of each node's queue data, by node id
 * @param nodesByStorageId the id of the node whose queue data has each storage id, by storage id
 */
record PlacementSnapshot(AffinityTopologyVersion affinityVersion,
                         long number,
                         ConcurrentHashMap<String, List<String>> replicas,
                         List<String> queueNodes,
                         Map<String, String> storageIds,
                         Map<String, String> nodesByStorageId) {
}
