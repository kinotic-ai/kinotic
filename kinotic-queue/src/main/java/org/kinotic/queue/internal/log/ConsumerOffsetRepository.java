package org.kinotic.queue.internal.log;

import lombok.Getter;
import org.apache.commons.io.IOUtils;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLongArray;
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
    /**
     * How long a position lasts unused: the queue's retention period.
     */
    @Getter
    private final Duration expiry;
    /**
     * {@link #refreshInterval(Duration)} for this queue's retention period.
     */
    @Getter
    private final Duration refreshInterval;
    // Counts the changes of the positions stored for each shard, so a change can be noticed without comparing positions
    private final AtomicLongArray versions;
    // Each consumer's position on each shard, by name
    private final Map<String, StoredPosition[]> positions = new HashMap<>();
    private final Map<String, FileChannel> channels = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, FileChannel> eldest) {
            boolean ret = size() > MAX_OPEN_FILES;
            if (ret) {
                // A failed close loses nothing: every offset was written before it, and the file is opened again when needed
                IOUtils.closeQuietly(eldest.getValue());
            }
            return ret;
        }
    };
    private boolean allLoaded;
    private boolean closed;

    /**
     * @param versions   one counter per shard, which every change of a position stored for the shard increments; it may
     *                   be shared with other repositories, so one version covers their positions too
     * @param syncWrites whether each save is forced to disk before it returns
     * @param properties the retention period after which an unused position expires
     */
    ConsumerOffsetRepository(Path directory, AtomicLongArray versions, boolean syncWrites, QueueProperties properties) {
        this.directory = directory;
        this.shardCount = versions.length();
        this.versions = versions;
        this.syncWrites = syncWrites;
        this.expiry = properties.getRetentionPeriod();
        this.refreshInterval = refreshInterval(expiry);
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
     * @return the next offset to deliver to the consumer on the shard; zero when the consumer has never committed
     * on it, or its position there expired, since a commit always stores an offset of at least one
     */
    public synchronized long findNextOffset(String consumerName, int shard) {
        StoredPosition stored = load(consumerName)[shard];
        return stored.isLive(expiredBefore()) ? stored.nextOffset() : 0;
    }

    /**
     * @return the position of every consumer whose position on the shard has not expired, by consumer name
     */
    public Map<String, ConsumerPosition> findAll(int shard) {
        return findChangedSince(shard, -1);
    }

    /**
     * @return the position of every consumer whose position on the shard changed after the version, a value its
     * counter had, and has not expired, by consumer name
     */
    public synchronized Map<String, ConsumerPosition> findChangedSince(int shard, long version) {
        loadAll();
        long expiredBefore = expiredBefore();
        Map<String, ConsumerPosition> ret = new HashMap<>();
        positions.forEach((consumerName, stored) -> {
            if (stored[shard].version() > version && stored[shard].isLive(expiredBefore)) {
                ret.put(consumerName, stored[shard].toPosition());
            }
        });
        return ret;
    }

    /**
     * Stores the next offset to deliver to the consumer on one shard, as used now, unless a later one is stored. Saving
     * the stored offset again only marks it used, once it was last used more than half a refresh interval ago.
     */
    public synchronized void save(String consumerName, int shard, long nextOffset) {
        long now = System.currentTimeMillis();
        StoredPosition stored = load(consumerName)[shard];
        long current = stored.isLive(now - expiry.toMillis()) ? stored.nextOffset() : 0;
        // Zero is the position of a consumer that never committed, which is not stored. Marking a position used costs a
        // forced write and a resend of the position, so a recent use is not marked again
        if (nextOffset > current
                || (nextOffset == current && current > 0 && stored.lastUsedMillis() < now - refreshInterval.toMillis() / 2)) {
            write(consumerName, shard, nextOffset, now);
        }
    }

    /**
     * Merges positions another copy of the shard holds: each keeps the later offset and the later use. An expired
     * position, received or stored, is ignored.
     */
    public synchronized void saveAll(int shard, Map<String, ConsumerPosition> received) {
        long expiredBefore = expiredBefore();
        received.forEach((consumerName, position) -> {
            StoredPosition stored = load(consumerName)[shard];
            // An expired position not deleted yet must not raise a consumer that started over
            boolean live = stored.isLive(expiredBefore);
            long storedOffset = live ? stored.nextOffset() : 0;
            long storedLastUsed = live ? stored.lastUsedMillis() : 0;
            if (position.lastUsedMillis() >= expiredBefore
                    && (position.nextOffset() > storedOffset || position.lastUsedMillis() > storedLastUsed)) {
                write(consumerName, shard, Math.max(position.nextOffset(), storedOffset),
                      Math.max(position.lastUsedMillis(), storedLastUsed));
            }
        });
    }

    /**
     * Deletes the expired positions, and the files of consumers left with none.
     */
    public synchronized void deleteExpired() {
        // A closed repository's directory may already hold the files of a queue created later with the same name
        if (closed) {
            return;
        }
        loadAll();
        long expiredBefore = expiredBefore();
        List<String> gone = new ArrayList<>();
        positions.forEach((consumerName, stored) -> {
            boolean anyLive = false;
            for (StoredPosition position : stored) {
                anyLive |= position.isLive(expiredBefore);
            }
            if (anyLive) {
                for (int shard = 0; shard < shardCount; shard++) {
                    if (stored[shard].nextOffset() > 0 && !stored[shard].isLive(expiredBefore)) {
                        write(consumerName, shard, 0, 0);
                    }
                }
            } else {
                gone.add(consumerName);
            }
        });
        for (String consumerName : gone) {
            positions.remove(consumerName);
            FileChannel channel = channels.remove(consumerName);
            IOUtils.closeQuietly(channel);
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
        channels.values().forEach(IOUtils::closeQuietly);
        channels.clear();
    }

    private long expiredBefore() {
        return System.currentTimeMillis() - expiry.toMillis();
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
        load(consumerName)[shard] = new StoredPosition(nextOffset, lastUsedMillis, versions.incrementAndGet(shard));
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

    private StoredPosition[] load(String consumerName) {
        return positions.computeIfAbsent(consumerName, name -> {
            StoredPosition[] ret = new StoredPosition[shardCount];
            Arrays.fill(ret, StoredPosition.NONE);
            Path file = directory.resolve(name);
            if (Files.exists(file)) {
                ByteBuffer buffer;
                try {
                    buffer = ByteBuffer.wrap(Files.readAllBytes(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                // The file ends after the highest shard ever committed, so later shards read as never committed
                for (int shard = 0; shard < shardCount && buffer.remaining() >= POSITION_BYTES; shard++) {
                    ret[shard] = new StoredPosition(buffer.getLong(), buffer.getLong(), 0);
                }
            }
            return ret;
        });
    }

    private FileChannel channel(String consumerName) throws IOException {
        FileChannel ret = channels.get(consumerName);
        if (ret == null) {
            DurableFiles.createDirectories(directory);
            Path file = directory.resolve(consumerName);
            boolean created = !Files.exists(file);
            ret = FileChannel.open(file,
                                   StandardOpenOption.CREATE,
                                   StandardOpenOption.READ,
                                   StandardOpenOption.WRITE);
            channels.put(consumerName, ret);
            if (created && syncWrites) {
                DurableFiles.force(directory);
            }
        }
        return ret;
    }
}
