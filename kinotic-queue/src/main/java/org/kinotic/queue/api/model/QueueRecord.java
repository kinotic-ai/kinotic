package org.kinotic.queue.api.model;

/**
 * A record read from a queue.
 *
 * @param position where the record sits in its queue
 * @param key      the key the record was appended with
 * @param payload  the bytes the record was appended with
 */
public record QueueRecord(QueuePosition position, String key, byte[] payload) {
}
