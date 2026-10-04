package org.kinotic.queue.internal.cluster;

import org.kinotic.queue.internal.log.ConsumerOffsetRepository;
import org.kinotic.queue.internal.log.ShardLog;

import java.util.Map;

/**
 * A shard this node owns or is placed to own.
 *
 * @param queue           the queue name
 * @param shard           the shard
 * @param shardLog        this node's copy of the shard
 * @param consumerOffsets the queue's consumer offsets on this node
 * @param groupOffsets    the queue's worker group offsets on this node
 * @param followers       the storage id of each node the shard's other copies are placed on, by node id, in placement
 *                        order
 * @param primary         whether the shard is placed on this node as its owner; an owner that is not keeps serving the
 *                        shard until the node placed as its owner takes it
 */
record ShardAssignment(String queue,
                       int shard,
                       ShardLog shardLog,
                       ConsumerOffsetRepository consumerOffsets,
                       ConsumerOffsetRepository groupOffsets,
                       Map<String, String> followers,
                       boolean primary) {

    String key() {
        return queue + "/" + shard;
    }

}
