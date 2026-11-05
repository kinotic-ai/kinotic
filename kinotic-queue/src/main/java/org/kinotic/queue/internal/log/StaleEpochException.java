package org.kinotic.queue.internal.log;

/**
 * Thrown when a node appends to a shard with an epoch the shard no longer accepts appends of: one older than an epoch
 * the shard has promised, which means another node has since taken ownership of the shard, or one an append failed
 * in, after which the shard's owner must take ownership again under a newer epoch.
 */
public class StaleEpochException extends IllegalStateException {

    public StaleEpochException(String message) {
        super(message);
    }

}
