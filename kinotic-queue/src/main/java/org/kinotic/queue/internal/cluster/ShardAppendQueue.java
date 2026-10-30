package org.kinotic.queue.internal.cluster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.kinotic.queue.internal.cluster.message.ShardId;

import java.util.ArrayDeque;

/**
 * The appends one client sends to one shard of one queue incarnation, as one producer: those waiting for the next
 * batch and the bytes they take, whether a batch is on its way, the sequence the next batch gets, and when the last
 * batch was sent, as {@link MonotonicClock#millis()} read it.
 */
@Getter
@Setter
@RequiredArgsConstructor
final class ShardAppendQueue {

    private final ShardId shardId;
    private final long producerId;
    private final ArrayDeque<QueuedAppend> waiting = new ArrayDeque<>();
    private long waitingBytes;
    private boolean inFlight;
    private long nextSequence;
    private long lastUsed = MonotonicClock.millis();
}
