package org.kinotic.queue.internal.log;

/**
 * Where a record sits in the batch of appends it was sent in, which lets a shard's owner recognize a batch it already
 * wrote when the batch is sent again.
 *
 * @param producerId identifies the node's client that sent the batch, for as long as that client runs
 * @param sequence   the batch's number among the batches the client sent to the shard; increases with every batch
 * @param index      the record's position in the batch
 * @param size       how many records the batch holds
 */
public record BatchSlot(long producerId, long sequence, int index, int size) {
}
