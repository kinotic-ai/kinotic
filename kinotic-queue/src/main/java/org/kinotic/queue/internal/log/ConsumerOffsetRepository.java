package org.kinotic.queue.internal.log;

import org.kinotic.queue.api.config.QueueProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Stores, for each consumer or worker group of one queue, its {@link ConsumerPosition position} on every shard. A
 * stored offset only moves forward, across power loss too when the repository forces its writes. A position not used
 * for the queue's retention period expires: it is found no more, never copied to other nodes, and deleted.
 */
public final class ConsumerOffsetRepository implements AutoCloseable {

    // Consumer and group names come from clients, so only the most recently written files stay open
    private static final int MAX_OPEN_FILES = 64;
    // Each shard's position is its next offset and when it was last used
    private static final int POSITION_BYTES = 2 * Long.BYTES;
    private static final Duration MAX_REFRESH_INTERVAL = Duration.ofHours(1);

    private final Path directory;
    private final int shardCount;
    private final boolean syncWrites;
    private final Duration expiry;
    // Counts the changes of the positions stored for each shard, so a change can be noticed without comparing positions
    private final long[] versions;
    // Next offset and last use of each shard, one after the other, by name
    private final Map<String, long[]> positions = new HashMap<>();
    private final Map<String, FileChannel> channels = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, FileChannel> eldest) {
            boolean ret = size() > MAX_OPEN_FILES;
            if (ret) {
                closeQuietly(eldest.getValue());
            }
            return ret;
        }
    };
    private boolean allLoaded;
    private boolean closed;

    /**
     * @param properties whether each save is forced to disk before it returns, and the retention period after which an
     *                   unused position expires
     */
    ConsumerOffsetRepository(Path directory, int shardCount, QueueProperties properties) {
        this.directory = directory;
        this.shardCount = shardCount;
        this.syncWrites = properties.isSyncWrites();
        this.expiry = properties.getRetentionPeriod();
        this.versions = new long[shardCount];
    }

    /**
     * @return how often a position still in use is used again, by a commit or a {@link #save} of the same offset, so it
     * never expires: well within the retention period
     */
    public static Duration refreshInterval(Duration retentionPeriod) {
        Duration quarter = retentionPeriod.dividedBy(4);
        return quarter.compareTo(MAX_REFRESH_INTERVAL) < 0 ? quarter : MAX_REFRESH_INTERVAL;
    }

    /**
     * @return {@link #refreshInterval(Duration)} for this queue's retention period
     */
    public Duration refreshInterval() {
        return refreshInterval(expiry);
    }

    /**
     * @return the next offset to deliver to the consumer on the shard; zero when the consumer has never committed
     * on it, or its position there expired, since a commit always stores an offset of at least one
     */
    public synchronized long findNextOffset(String consumerName, int shard) {
        long[] stored = load(consumerName);
        return isLive(stored, shard) ? stored[2 * shard] : 0;
    }

    /**
     * @return the position of every consumer whose position on the shard has not expired, by consumer name
     */
    public synchronized Map<String, ConsumerPosition> findAll(int shard) {
        loadAll();
        Map<String, ConsumerPosition> ret = new HashMap<>();
        positions.forEach((consumerName, stored) -> {
            if (isLive(stored, shard)) {
                ret.put(consumerName, new ConsumerPosition(stored[2 * shard], stored[2 * shard + 1]));
            }
        });
        return ret;
    }

    /**
     * @return a number that grows whenever a position stored for the shard changes
     */
    public synchronized long version(int shard) {
        return versions[shard];
    }

    /**
     * Stores the next offset to deliver to the consumer on one shard, as used now, unless a later one is stored. Saving
     * the stored offset again only marks it used.
     */
    public synchronized void save(String consumerName, int shard, long nextOffset) {
        long[] stored = load(consumerName);
        long current = isLive(stored, shard) ? stored[2 * shard] : 0;
        // Zero is the position of a consumer that never committed, which is not stored
        if (nextOffset > 0 && nextOffset >= current) {
            write(consumerName, shard, nextOffset, System.currentTimeMillis());
        }
    }

    /**
     * Merges positions another copy of the shard holds: each keeps the later offset and the later use. An expired
     * position is ignored.
     */
    public synchronized void saveAll(int shard, Map<String, ConsumerPosition> received) {
        long expiredBefore = expiredBefore();
        received.forEach((consumerName, position) -> {
            long[] stored = load(consumerName);
            if (position.lastUsedMillis() >= expiredBefore
                    && (position.nextOffset() > stored[2 * shard] || position.lastUsedMillis() > stored[2 * shard + 1])) {
                write(consumerName, shard, Math.max(position.nextOffset(), stored[2 * shard]),
                      Math.max(position.lastUsedMillis(), stored[2 * shard + 1]));
            }
        });
    }

    /**
     * Deletes the expired positions, and the files of consumers left with none.
     */
    public synchronized void deleteExpired() {
        loadAll();
        long expiredBefore = expiredBefore();
        List<String> gone = new ArrayList<>();
        positions.forEach((consumerName, stored) -> {
            boolean any = false;
            for (int shard = 0; shard < shardCount; shard++) {
                if (stored[2 * shard] > 0 && stored[2 * shard + 1] < expiredBefore) {
                    write(consumerName, shard, 0, 0);
                }
                any |= stored[2 * shard] > 0;
            }
            if (!any) {
                gone.add(consumerName);
            }
        });
        for (String consumerName : gone) {
            positions.remove(consumerName);
            FileChannel channel = channels.remove(consumerName);
            if (channel != null) {
                closeQuietly(channel);
            }
            try {
                Files.deleteIfExists(directory.resolve(consumerName));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        channels.values().forEach(ConsumerOffsetRepository::closeQuietly);
        channels.clear();
    }

    // A failed close loses nothing: every offset was written before it, and the file is opened again when needed
    private static void closeQuietly(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // The channel is released either way
        }
    }

    private long expiredBefore() {
        return System.currentTimeMillis() - expiry.toMillis();
    }

    private boolean isLive(long[] stored, int shard) {
        return stored[2 * shard] > 0 && stored[2 * shard + 1] >= expiredBefore();
    }

    private void write(String consumerName, int shard, long nextOffset, long lastUsedMillis) {
        // A closed repository's directory may already hold the files of a queue created later with the same name
        if (closed) {
            throw new IllegalStateException("The offsets in " + directory + " are closed");
        }
        ByteBuffer buffer = ByteBuffer.allocate(POSITION_BYTES).putLong(0, nextOffset).putLong(Long.BYTES, lastUsedMillis);
        try {
            FileChannel channel = channel(consumerName);
            while (buffer.hasRemaining()) {
                channel.write(buffer, (long) shard * POSITION_BYTES + buffer.position());
            }
            if (syncWrites) {
                // With metadata, since a commit on a higher shard grows the file
                channel.force(true);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        long[] stored = load(consumerName);
        stored[2 * shard] = nextOffset;
        stored[2 * shard + 1] = lastUsedMillis;
        versions[shard]++;
    }

    private void loadAll() {
        if (!allLoaded) {
            if (Files.isDirectory(directory)) {
                try (Stream<Path> files = Files.list(directory)) {
                    files.map(file -> file.getFileName().toString())
                         .filter(QueueLog::isValidName)
                         .forEach(this::load);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            allLoaded = true;
        }
    }

    private long[] load(String consumerName) {
        return positions.computeIfAbsent(consumerName, name -> {
            long[] ret = new long[2 * shardCount];
            Path file = directory.resolve(name);
            if (Files.exists(file)) {
                ByteBuffer buffer;
                try {
                    buffer = ByteBuffer.wrap(Files.readAllBytes(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                // The file ends after the highest shard ever committed, so later shards read as never committed
                for (int i = 0; i < 2 * shardCount && buffer.remaining() >= Long.BYTES; i++) {
                    ret[i] = buffer.getLong();
                }
            }
            return ret;
        });
    }

    private FileChannel channel(String consumerName) throws IOException {
        FileChannel ret = channels.get(consumerName);
        if (ret == null) {
            ShardLog.createDirectories(directory);
            Path file = directory.resolve(consumerName);
            boolean created = !Files.exists(file);
            ret = FileChannel.open(file,
                                   StandardOpenOption.CREATE,
                                   StandardOpenOption.READ,
                                   StandardOpenOption.WRITE);
            channels.put(consumerName, ret);
            if (created && syncWrites) {
                ShardLog.force(directory);
            }
        }
        return ret;
    }
}
