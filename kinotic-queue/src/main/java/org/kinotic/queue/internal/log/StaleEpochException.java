package org.kinotic.queue.internal.log;

/**
 * Thrown when a node appends to a shard with an epoch older than one the shard has promised, which means another
 * node has since taken ownership of the shard.
 */
public class StaleEpochException extends IllegalStateException {

    public StaleEpochException(long epoch, long acceptedEpoch) {
        super("Epoch " + epoch + " is older than the promised epoch " + acceptedEpoch);
    }

}
