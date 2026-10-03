package org.kinotic.queue.internal.log;

/**
 * Thrown when a node appends to a shard with an ownership epoch older than one the shard has already accepted,
 * which means another node has since taken ownership of the shard.
 */
public class StaleEpochException extends IllegalStateException {

    public StaleEpochException(long epoch, long acceptedEpoch) {
        super("Epoch " + epoch + " is older than the accepted epoch " + acceptedEpoch);
    }

}
