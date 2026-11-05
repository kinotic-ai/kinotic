package org.kinotic.queue.internal.cluster;

import org.kinotic.queue.internal.cluster.message.ShardId;
import org.kinotic.queue.internal.log.QueueLog;
import org.kinotic.queue.internal.log.ShardLog;

import java.util.Map;

/**
 * A shard this node owns or is placed to own.
 *
 * @param shardId   the shard
 * @param queueLog  this node's copy of the shard's queue
 * @param shardLog  this node's copy of the shard
 * @param followers the storage id of each node the shard's other copies are placed on, by node id, in placement
 *                  order
 * @param primary   whether the shard is placed on this node as its owner; an owner that is not keeps serving the
 *                  shard until the node placed as its owner takes it
 */
record ShardAssignment(ShardId shardId,
                       QueueLog queueLog,
                       ShardLog shardLog,
                       Map<String, String> followers,
                       boolean primary) {
}
