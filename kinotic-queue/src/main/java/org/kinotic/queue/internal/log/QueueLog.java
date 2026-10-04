package org.kinotic.queue.internal.log;

import net.openhft.hashing.LongHashFunction;
import org.apache.commons.io.FileUtils;
import org.kinotic.queue.api.config.QueueProperties;
import org.kinotic.queue.api.model.QueueDefinition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The shards of a queue this node holds and the committed offsets of the queue's consumers, kept under one
 * directory. Shards are created when first used, so a node holds only the shards placed on it.
 */
public final class QueueLog implements AutoCloseable {

    private static final String SHARD_COUNT_FILE = "shard-count";
    private static final String INCARNATION_FILE = "incarnation";
    // The incarnations of deleted queues this node knows of, one "<incarnation> <name>" per line
    private static final String DELETED_QUEUES_FILE = ".deleted-queues";
    // Starts with a dot, which no queue name does, so it is never taken for a queue's directory
    private static final String STORAGE_ID_FILE = ".storage-id";
    // Queue and consumer names become directory and file names, so they can never contain a path separator or "..",
    // which also rules out path traversal
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final Path directory;
    private final QueueDefinition definition;
    private final String incarnation;
    private final QueueProperties properties;
    private final ShardLog[] shards;
    private final ConsumerOffsetRepository consumerOffsets;
    private final ConsumerOffsetRepository groupOffsets;

    private QueueLog(Path directory, QueueDefinition definition, String incarnation, QueueProperties properties) {
        this.directory = directory;
        this.definition = definition;
        this.incarnation = incarnation;
        this.properties = properties;
        this.shards = new ShardLog[definition.shardCount()];
        this.consumerOffsets = new ConsumerOffsetRepository(directory.resolve("consumers"), definition.shardCount(), properties.isSyncWrites());
        this.groupOffsets = new ConsumerOffsetRepository(directory.resolve("groups"), definition.shardCount(), properties.isSyncWrites());
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
     * Opens the queue stored in {@code directory}, storing {@code definition} there when the directory holds none, or
     * holds an earlier queue of the same name, whose data is deleted.
     *
     * @param incarnation tells this queue from earlier ones of the same name
     * @param properties  the queue storage settings the shards follow
     * @throws IllegalStateException when the directory holds the queue with a different shard count
     */
    public static QueueLog openOrCreate(Path directory, QueueDefinition definition, String incarnation, QueueProperties properties) {
        if (Files.exists(directory.resolve(SHARD_COUNT_FILE)) && !incarnation.equals(findIncarnation(directory))) {
            delete(directory);
        }
        QueueDefinition stored = findDefinition(directory);
        if (stored != null && stored.shardCount() != definition.shardCount()) {
            throw new IllegalStateException(directory + " holds queue " + definition.name() + " with " + stored.shardCount()
                                                    + " shards, but the cluster defines it with " + definition.shardCount());
        }
        if (stored == null) {
            // The incarnation first, since a directory without a shard count holds no queue
            ShardLog.writeDurably(directory.resolve(INCARNATION_FILE), incarnation);
            ShardLog.writeDurably(directory.resolve(SHARD_COUNT_FILE), String.valueOf(definition.shardCount()));
        }
        return new QueueLog(directory, definition, incarnation, properties);
    }

    /**
     * @return the incarnation of the queue stored in {@code directory}, or null when no queue is stored there
     */
    public static String findIncarnation(Path directory) {
        Path file = directory.resolve(INCARNATION_FILE);
        String ret = null;
        if (Files.exists(file)) {
            try {
                ret = Files.readString(file).trim();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return ret;
    }

    /**
     * Deletes the queue stored in {@code directory}, which must not be open.
     */
    public static void delete(Path directory) {
        try {
            // The shard count first, so a crash partway leaves a directory that holds no queue
            Files.deleteIfExists(directory.resolve(SHARD_COUNT_FILE));
            FileUtils.deleteDirectory(directory.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @return the names of the deleted queues this node recorded in {@code dataDirectory}, by incarnation
     */
    public static Map<String, String> findDeletedQueues(Path dataDirectory) {
        Path file = dataDirectory.resolve(DELETED_QUEUES_FILE);
        Map<String, String> ret = new HashMap<>();
        if (Files.exists(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String[] fields = line.trim().split(" ");
                    if (fields.length == 2) {
                        ret.put(fields[0], fields[1]);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return ret;
    }

    /**
     * Records in {@code dataDirectory} the deleted queues, by incarnation, which a node restarted later still knows.
     */
    public static void saveDeletedQueues(Path dataDirectory, Map<String, String> deletedQueues) {
        List<String> lines = new ArrayList<>();
        deletedQueues.forEach((incarnation, name) -> lines.add(incarnation + " " + name));
        ShardLog.writeDurably(dataDirectory.resolve(DELETED_QUEUES_FILE), String.join("\n", lines));
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
     * Identifies the queue data stored in {@code dataDirectory}, creating the id when the directory holds none. The id
     * stays the same across restarts and changes when the directory is emptied, so it tells a node that kept its data
     * from one that lost it.
     */
    public static String storageId(Path dataDirectory) {
        Path file = dataDirectory.resolve(STORAGE_ID_FILE);
        String ret;
        try {
            if (Files.exists(file)) {
                ret = Files.readString(file).trim();
            } else {
                ret = UUID.randomUUID().toString();
                ShardLog.writeDurably(file, ret);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
            shards[shard] = new ShardLog(shardDirectory(shard), properties);
        }
        return shards[shard];
    }

    /**
     * @return the shards this node has opened, by shard
     */
    public synchronized Map<Integer, ShardLog> openShards() {
        Map<Integer, ShardLog> ret = new HashMap<>();
        for (int shard = 0; shard < shards.length; shard++) {
            if (shards[shard] != null) {
                ret.put(shard, shards[shard]);
            }
        }
        return ret;
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
     * @return what tells this queue from earlier ones of the same name
     */
    public String incarnation() {
        return incarnation;
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
