package org.kinotic.queue.internal.cluster;

import org.kinotic.queue.internal.log.ShardLog;

import java.util.List;

/**
 * A shard placed on this node as its owner.
 *
 * @param queue     the queue name
 * @param shard     the shard
 * @param log       this node's copy of the shard
 * @param followers the ids of the nodes holding the shard's other copies
 */
record ShardAssignment(String queue, int shard, ShardLog log, List<String> followers) {

    String key() {
        return queue + "/" + shard;
    }

}
