package org.kinotic.queue.internal.log;

import lombok.Getter;
import lombok.experimental.Accessors;
import net.openhft.hashing.LongHashFunction;
import org.apache.commons.io.FileUtils;
import org.kinotic.queue.api.config.QueueProperties;
import org.kinotic.queue.api.model.QueueDefinition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.regex.Pattern;

/**
 * The shards of a queue this node holds and the committed offsets of the queue's consumers, kept under one
 * directory. Shards are created when first used, so a node holds only the shards placed on it. A closed queue opens no
 * shard and makes no promise, failing with an {@link IllegalStateException}.
 */
@Accessors(fluent = true)
public final class QueueLog implements AutoCloseable {

    // "<incarnation> <shard count>"
    private static final String QUEUE_FILE = "queue";
    // The incarnations of deleted queues this node knows of, one "<incarnation> <name>" per line
    private static final String DELETED_QUEUES_FILE = ".deleted-queues";
    // Starts with a dot, which no queue name does, so it is never taken for a queue's directory
    private static final String STORAGE_ID_FILE = ".storage-id";
    // Queue and consumer names become directory and file names, so they can never contain a path separator or "..",
    // which also rules out path traversal
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final Path directory;
    @Getter
    private final QueueDefinition definition;
    /**
     * What tells this queue from earlier ones of the same name.
     */
    @Getter
    private final String incarnation;
    private final QueueProperties properties;
    private final ShardLog[] shards;
    // Counts the changes of the positions stored for each shard, by consumers and groups alike
    private final AtomicLongArray positionVersions;
    /**
     * The committed offsets of the queue's subscribed consumers.
     */
    @Getter
    private final ConsumerOffsetRepository consumerOffsets;
    /**
     * The low watermarks of the queue's worker groups, kept apart from consumers so the two never share a name.
     */
    @Getter
    private final ConsumerOffsetRepository groupOffsets;
    private boolean closed;

    private QueueLog(Path directory, QueueDefinition definition, String incarnation, QueueProperties properties) {
        this.directory = directory;
        this.definition = definition;
        this.incarnation = incarnation;
        this.properties = properties;
        this.shards = new ShardLog[definition.shardCount()];
        this.positionVersions = new AtomicLongArray(definition.shardCount());
        this.consumerOffsets = new ConsumerOffsetRepository(directory.resolve("consumers"), positionVersions, properties);
        this.groupOffsets = new ConsumerOffsetRepository(directory.resolve("groups"), positionVersions, properties);
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
        // Anything else there belongs to an earlier queue, or to a deletion a crash interrupted
        if (Files.isDirectory(directory) && !incarnation.equals(findIncarnation(directory))) {
            delete(directory);
        }
        QueueDefinition stored = findDefinition(directory);
        if (stored != null && stored.shardCount() != definition.shardCount()) {
            throw new IllegalStateException(directory + " holds queue " + definition.name() + " with " + stored.shardCount()
                                                    + " shards, but the cluster defines it with " + definition.shardCount());
        }
        if (stored == null) {
            DurableFiles.writeDurably(directory.resolve(QUEUE_FILE), incarnation + " " + definition.shardCount());
        }
        return new QueueLog(directory, definition, incarnation, properties);
    }

    /**
     * @return the incarnation of the queue stored in {@code directory}, or null when no queue is stored there
     */
    public static String findIncarnation(Path directory) {
        String stored = DurableFiles.read(directory.resolve(QUEUE_FILE));
        return stored != null ? stored.split(" ")[0] : null;
    }

    /**
     * Deletes the queue stored in {@code directory}, which must not be open.
     */
    public static void delete(Path directory) {
        try {
            // The queue file first, so a crash partway leaves a directory that holds no queue
            Files.deleteIfExists(directory.resolve(QUEUE_FILE));
            FileUtils.deleteDirectory(directory.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @return the names of the deleted queues this node recorded in {@code dataDirectory}, by incarnation
     */
    public static Map<String, String> findDeletedQueues(Path dataDirectory) {
        Map<String, String> ret = new HashMap<>();
        for (String line : DurableFiles.readLines(dataDirectory.resolve(DELETED_QUEUES_FILE))) {
            String[] fields = line.trim().split(" ");
            if (fields.length == 2) {
                ret.put(fields[0], fields[1]);
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
        DurableFiles.writeDurably(dataDirectory.resolve(DELETED_QUEUES_FILE), String.join("\n", lines));
    }

    /**
     * Reads the definition of the queue stored in {@code directory}.
     *
     * @return the definition, or null when no queue is stored there
     */
    public static QueueDefinition findDefinition(Path directory) {
        String name = directory.getFileName().toString();
        String stored = isValidName(name) ? DurableFiles.read(directory.resolve(QUEUE_FILE)) : null;
        return stored != null ? new QueueDefinition(name, Integer.parseInt(stored.split(" ")[1])) : null;
    }

    /**
     * Identifies the queue data stored in {@code dataDirectory}, creating the id when the directory holds none. The id
     * stays the same across restarts and changes when the directory is emptied, so it tells a node that kept its data
     * from one that lost it.
     */
    public static String storageId(Path dataDirectory) {
        Path file = dataDirectory.resolve(STORAGE_ID_FILE);
        String ret = DurableFiles.read(file);
        if (ret == null) {
            ret = UUID.randomUUID().toString();
            DurableFiles.writeDurably(file, ret);
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
        requireOpen();
        if (shards[shard] == null) {
            shards[shard] = new ShardLog(shardDirectory(shard), properties);
        }
        return shards[shard];
    }

    /**
     * @return about how many bytes the entries of the shards this node has opened take, as {@link ShardLog#bytes()}
     */
    public synchronized long bytes() {
        long ret = 0;
        for (ShardLog shard : shards) {
            if (shard != null) {
                ret += shard.bytes();
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
        requireOpen();
        ShardLog copy = findShard(shard);
        return copy != null ? copy.promise(epoch) : ShardLog.promiseWithoutCopy(shardDirectory(shard), epoch);
    }

    /**
     * @return the positions of the consumers and worker groups on the shard that have not expired
     */
    public ShardPositions positions(int shard) {
        return new ShardPositions(consumerOffsets.findAll(shard), groupOffsets.findAll(shard));
    }

    /**
     * Merges the positions another copy of the shard holds, as {@link ConsumerOffsetRepository#saveAll} does.
     */
    public void savePositions(int shard, ShardPositions positions) {
        consumerOffsets.saveAll(shard, positions.consumers());
        groupOffsets.saveAll(shard, positions.groups());
    }

    /**
     * @return a number that grows whenever a position stored for the shard changes; zero before any did since the queue
     * opened. Reading it never waits for a write to the positions.
     */
    public long positionsVersion(int shard) {
        return positionVersions.get(shard);
    }

    /**
     * @return the positions on the shard that changed after the {@link #positionsVersion version} and have not
     * expired; every position when the version is -1
     */
    public ShardPositions positionsChangedSince(int shard, long version) {
        return new ShardPositions(consumerOffsets.findChangedSince(shard, version), groupOffsets.findChangedSince(shard, version));
    }

    /**
     * Deletes the expired positions of the queue's consumers and worker groups.
     */
    public void deleteExpiredPositions() {
        consumerOffsets.deleteExpired();
        groupOffsets.deleteExpired();
    }

    @Override
    public synchronized void close() {
        closed = true;
        consumerOffsets.close();
        groupOffsets.close();
        for (ShardLog shard : shards) {
            if (shard != null) {
                shard.close();
            }
        }
    }

    // A closed queue's directory may already hold the copy of a queue created later with the same name
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Queue " + directory + " is closed");
        }
    }

    private Path shardDirectory(int shard) {
        return directory.resolve("shards").resolve(String.valueOf(shard));
    }
}
