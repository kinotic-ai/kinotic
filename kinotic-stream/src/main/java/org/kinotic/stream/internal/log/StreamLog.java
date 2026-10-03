package org.kinotic.stream.internal.log;

import net.openhft.hashing.LongHashFunction;
import org.kinotic.stream.api.model.StreamDefinition;
import org.kinotic.stream.api.model.StreamPosition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * A stream stored on this node: its shards and its consumers' committed offsets, kept under one directory.
 */
public final class StreamLog implements AutoCloseable {

    private static final String SHARD_COUNT_FILE = "shard-count";

    private final StreamDefinition definition;
    private final ShardLog[] shards;
    private final ConsumerOffsetRepository consumerOffsets;

    private StreamLog(Path directory, StreamDefinition definition) {
        this.definition = definition;
        this.shards = new ShardLog[definition.shardCount()];
        for (int i = 0; i < shards.length; i++) {
            shards[i] = new ShardLog(definition.name(), i, directory.resolve("shards").resolve(String.valueOf(i)));
        }
        this.consumerOffsets = new ConsumerOffsetRepository(directory.resolve("consumers"), shards.length);
    }

    /**
     * Opens the stream stored in {@code directory}.
     *
     * @throws IllegalArgumentException when no stream is stored there
     */
    public static StreamLog open(Path directory, String name) {
        Path shardCountFile = directory.resolve(SHARD_COUNT_FILE);
        if (!Files.exists(shardCountFile)) {
            throw new IllegalArgumentException("No stream named " + name);
        }
        return new StreamLog(directory, new StreamDefinition(name, readShardCount(shardCountFile)));
    }

    /**
     * Opens the stream stored in {@code directory}, creating it from {@code definition} when none is stored there.
     */
    public static StreamLog openOrCreate(Path directory, StreamDefinition definition) {
        Path shardCountFile = directory.resolve(SHARD_COUNT_FILE);
        StreamLog ret;
        if (Files.exists(shardCountFile)) {
            ret = new StreamLog(directory, new StreamDefinition(definition.name(), readShardCount(shardCountFile)));
        } else {
            try {
                Files.createDirectories(directory);
                // Written beside the target then moved, so a crash never leaves a partial shard count
                Path tempFile = directory.resolve(SHARD_COUNT_FILE + ".tmp");
                Files.writeString(tempFile, String.valueOf(definition.shardCount()));
                Files.move(tempFile, shardCountFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            ret = new StreamLog(directory, definition);
        }
        return ret;
    }

    /**
     * Appends a record to the shard its key hashes to.
     */
    public StreamPosition append(String key, byte[] payload) {
        int shard = (int) Math.floorMod(LongHashFunction.xx3().hashChars(key), (long) shards.length);
        return shards[shard].append(key, payload);
    }

    public StreamDefinition definition() {
        return definition;
    }

    public ShardLog shard(int shard) {
        return shards[shard];
    }

    public ConsumerOffsetRepository consumerOffsets() {
        return consumerOffsets;
    }

    @Override
    public void close() {
        consumerOffsets.close();
        for (ShardLog shard : shards) {
            shard.close();
        }
    }

    private static int readShardCount(Path shardCountFile) {
        try {
            return Integer.parseInt(Files.readString(shardCountFile).trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
