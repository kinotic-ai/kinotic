package org.kinotic.stream.api.model;

/**
 * A record read from a stream.
 *
 * @param position where the record sits in its stream
 * @param key      the key the record was appended with
 * @param payload  the bytes the record was appended with
 */
public record StreamRecord(StreamPosition position, String key, byte[] payload) {
}
