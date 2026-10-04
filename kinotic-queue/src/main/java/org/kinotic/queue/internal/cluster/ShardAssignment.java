package org.kinotic.queue.internal.cluster;

import org.kinotic.queue.internal.log.ConsumerOffsetRepository;
import org.kinotic.queue.internal.log.ShardLog;

import java.util.List;

/**
 * A shard placed on this node as its owner.
 *
 * @param queue           the queue name
 * @param shard           the shard
 * @param shardLog        this node's copy of the shard
 * @param consumerOffsets the queue's consumer offsets on this node
 * @param groupOffsets    the queue's worker group offsets on this node
 * @param followers       the ids of the nodes holding the shard's other copies
 */
record ShardAssignment(String queue,
                       int shard,
                       ShardLog shardLog,
                       ConsumerOffsetRepository consumerOffsets,
                       ConsumerOffsetRepository groupOffsets,
                       List<String> followers) {

    String key() {
        return queue + "/" + shard;
    }

}
