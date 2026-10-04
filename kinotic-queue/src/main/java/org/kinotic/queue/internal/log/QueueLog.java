package org.kinotic.queue.internal.log;

import net.openhft.hashing.LongHashFunction;
import org.kinotic.queue.api.model.QueueDefinition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * The shards of a queue this node holds and the committed offsets of the queue's consumers, kept under one
 * directory. Shards are created when first used, so a node holds only the shards placed on it.
 */
public final class QueueLog implements AutoCloseable {

    private static final String SHARD_COUNT_FILE = "shard-count";
    // Queue and consumer names become directory and file names, so they can never contain a path separator or "..",
    // which also rules out path traversal
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final Path directory;
    private final QueueDefinition definition;
    private final boolean syncWrites;
    private final ShardLog[] shards;
    private final ConsumerOffsetRepository consumerOffsets;
    private final ConsumerOffsetRepository groupOffsets;

    private QueueLog(Path directory, QueueDefinition definition, boolean syncWrites) {
        this.directory = directory;
        this.definition = definition;
        this.syncWrites = syncWrites;
        this.shards = new ShardLog[definition.shardCount()];
        this.consumerOffsets = new ConsumerOffsetRepository(directory.resolve("consumers"), definition.shardCount());
        this.groupOffsets = new ConsumerOffsetRepository(directory.resolve("groups"), definition.shardCount());
    }

    /**
     * @return whether a queue or consumer may have this name: up to 128 letters, digits, {@code .}, {@code _} or {@code -},
     * starting with a letter or digit
     */
    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    /**
     * @throws IllegalArgumentException when the name is not {@link #isValidName valid}
     */
    public static void requireValidName(String name) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid name '" + name + "': use up to 128 letters, digits, '.', '_' or '-', starting with a letter or digit");
        }
    }

    /**
     * Opens the queue stored in {@code directory}, storing {@code definition} there when the directory holds none.
     *
     * @param syncWrites whether the queue's shards force each write to disk before acknowledging it
     * @throws IllegalStateException when the directory holds the queue with a different shard count
     */
    public static QueueLog openOrCreate(Path directory, QueueDefinition definition, boolean syncWrites) {
        QueueDefinition stored = findDefinition(directory);
        if (stored != null && stored.shardCount() != definition.shardCount()) {
            throw new IllegalStateException(directory + " holds queue " + definition.name() + " with " + stored.shardCount()
                                                    + " shards, but the cluster defines it with " + definition.shardCount());
        }
        if (stored == null) {
            ShardLog.writeDurably(directory.resolve(SHARD_COUNT_FILE), String.valueOf(definition.shardCount()));
        }
        return new QueueLog(directory, definition, syncWrites);
    }

    /**
     * Reads the definition of the queue stored in {@code directory}.
     *
     * @return the definition, or null when no queue is stored there
     */
    public static QueueDefinition findDefinition(Path directory) {
        Path shardCountFile = directory.resolve(SHARD_COUNT_FILE);
        QueueDefinition ret = null;
        if (isValidName(directory.getFileName().toString()) && Files.exists(shardCountFile)) {
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
            shards[shard] = new ShardLog(shardDirectory(shard), syncWrites);
        }
        return shards[shard];
    }

    /**
     * @return the shard, or null when this node has never held it
     */
    public synchronized ShardLog findShard(int shard) {
        ShardLog ret = shards[shard];
        if (ret == null && ShardLog.exists(shardDirectory(shard))) {
            ret = shard(shard);
        }
        return ret;
    }

    /**
     * Promises not to accept entries of the shard from an owner older than {@code epoch}, whether or not this node
     * holds a copy of the shard. The promise survives restarts and holds for a copy created later.
     *
     * @return the newest epoch promised for the shard before this call, or -1 when none was
     */
    public synchronized long promise(int shard, long epoch) {
        ShardLog copy = findShard(shard);
        return copy != null ? copy.promise(epoch) : ShardLog.promiseWithoutCopy(shardDirectory(shard), epoch);
    }

    public QueueDefinition definition() {
        return definition;
    }

    /**
     * @return the committed offsets of the queue's subscribed consumers
     */
    public ConsumerOffsetRepository consumerOffsets() {
        return consumerOffsets;
    }

    /**
     * @return the low watermarks of the queue's worker groups, kept apart from consumers so the two never share a name
     */
    public ConsumerOffsetRepository groupOffsets() {
        return groupOffsets;
    }

    @Override
    public synchronized void close() {
        consumerOffsets.close();
        groupOffsets.close();
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
