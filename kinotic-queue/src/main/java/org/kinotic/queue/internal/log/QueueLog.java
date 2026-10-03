package org.kinotic.queue.internal.log;

import net.openhft.hashing.LongHashFunction;
import org.kinotic.queue.api.model.QueueDefinition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The shards of a queue this node holds and the committed offsets of the queue's consumers, kept under one
 * directory. Shards are created when first used, so a node holds only the shards placed on it.
 */
public final class QueueLog implements AutoCloseable {

    private static final String SHARD_COUNT_FILE = "shard-count";

    private final Path directory;
    private final QueueDefinition definition;
    private final ShardLog[] shards;
    private final ConsumerOffsetRepository consumerOffsets;

    private QueueLog(Path directory, QueueDefinition definition) {
        this.directory = directory;
        this.definition = definition;
        this.shards = new ShardLog[definition.shardCount()];
        this.consumerOffsets = new ConsumerOffsetRepository(directory.resolve("consumers"), definition.shardCount());
    }

    /**
     * Opens the queue stored in {@code directory}, storing {@code definition} there when the directory holds none.
     */
    public static QueueLog openOrCreate(Path directory, QueueDefinition definition) {
        Path shardCountFile = directory.resolve(SHARD_COUNT_FILE);
        if (!Files.exists(shardCountFile)) {
            try {
                Files.createDirectories(directory);
                // Written beside the target then moved, so a crash never leaves a partial shard count
                Path tempFile = directory.resolve(SHARD_COUNT_FILE + ".tmp");
                Files.writeString(tempFile, String.valueOf(definition.shardCount()));
                Files.move(tempFile, shardCountFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new QueueLog(directory, definition);
    }

    /**
     * Reads the definition of the queue stored in {@code directory}.
     *
     * @return the definition, or null when no queue is stored there
     */
    public static QueueDefinition findDefinition(Path directory) {
        Path shardCountFile = directory.resolve(SHARD_COUNT_FILE);
        QueueDefinition ret = null;
        if (Files.exists(shardCountFile)) {
            try {
                ret = new QueueDefinition(directory.getFileName().toString(),
                                          Integer.parseInt(Files.readString(shardCountFile).trim()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return ret;
    }

    /**
     * @return the shard a key belongs to; every node computes the same shard for the same key
     */
    public static int shardOf(String key, int shardCount) {
        return (int) Math.floorMod(LongHashFunction.xx3().hashChars(key), (long) shardCount);
    }

    /**
     * Opens the shard, creating it when this node does not hold it yet.
     */
    public synchronized ShardLog shard(int shard) {
        if (shards[shard] == null) {
            shards[shard] = new ShardLog(shardDirectory(shard));
        }
        return shards[shard];
    }

    /**
     * @return the shard, or null when this node has never held it
     */
    public synchronized ShardLog findShard(int shard) {
        ShardLog ret = shards[shard];
        if (ret == null && Files.isDirectory(shardDirectory(shard))) {
            ret = shard(shard);
        }
        return ret;
    }

    public QueueDefinition definition() {
        return definition;
    }

    public ConsumerOffsetRepository consumerOffsets() {
        return consumerOffsets;
    }

    @Override
    public synchronized void close() {
        consumerOffsets.close();
        for (ShardLog shard : shards) {
            if (shard != null) {
                shard.close();
            }
        }
    }

    private Path shardDirectory(int shard) {
        return directory.resolve("shards").resolve(String.valueOf(shard));
    }
}
