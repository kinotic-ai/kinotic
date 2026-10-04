package org.kinotic.queue.internal.log;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Stores, for each consumer or worker group of one queue, the next offset to deliver on every shard. A stored offset
 * only moves forward.
 */
public final class ConsumerOffsetRepository implements AutoCloseable {

    // Consumer and group names come from clients, so only the most recently written files stay open
    private static final int MAX_OPEN_FILES = 64;

    private final Path directory;
    private final int shardCount;
    // Counts the stored offsets that moved forward on each shard, so a change can be noticed without comparing offsets
    private final long[] versions;
    private final Map<String, long[]> offsets = new HashMap<>();
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

    ConsumerOffsetRepository(Path directory, int shardCount) {
        this.directory = directory;
        this.shardCount = shardCount;
        this.versions = new long[shardCount];
    }

    /**
     * @return the next offset to deliver to the consumer on the shard; zero when the consumer has never committed
     * on it, since a commit always stores an offset of at least one
     */
    public synchronized long findNextOffset(String consumerName, int shard) {
        return load(consumerName)[shard];
    }

    /**
     * @return the next offset of every consumer that has committed on the shard, by consumer name
     */
    public synchronized Map<String, Long> findAll(int shard) {
        loadAll();
        Map<String, Long> ret = new HashMap<>();
        offsets.forEach((consumerName, stored) -> {
            if (stored[shard] > 0) {
                ret.put(consumerName, stored[shard]);
            }
        });
        return ret;
    }

    /**
     * @return a number that grows whenever an offset stored for the shard moves forward
     */
    public synchronized long version(int shard) {
        return versions[shard];
    }

    /**
     * Stores the next offset to deliver to the consumer on one shard, unless a later one is already stored.
     */
    public synchronized void save(String consumerName, int shard, long nextOffset) {
        long[] stored = load(consumerName);
        if (nextOffset > stored[shard]) {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).putLong(0, nextOffset);
            try {
                FileChannel channel = channel(consumerName);
                while (buffer.hasRemaining()) {
                    channel.write(buffer, (long) shard * Long.BYTES + buffer.position());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            stored[shard] = nextOffset;
            versions[shard]++;
        }
    }

    /**
     * {@link #save Saves} every consumer's next offset on one shard.
     */
    public synchronized void saveAll(int shard, Map<String, Long> nextOffsets) {
        nextOffsets.forEach((consumerName, nextOffset) -> save(consumerName, shard, nextOffset));
    }

    @Override
    public synchronized void close() {
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
        return offsets.computeIfAbsent(consumerName, name -> {
            long[] ret = new long[shardCount];
            Path file = directory.resolve(name);
            if (Files.exists(file)) {
                ByteBuffer buffer;
                try {
                    buffer = ByteBuffer.wrap(Files.readAllBytes(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                // The file ends after the highest shard ever committed, so later shards read as never committed
                for (int i = 0; i < shardCount && buffer.remaining() >= Long.BYTES; i++) {
                    ret[i] = buffer.getLong();
                }
            }
            return ret;
        });
    }

    private FileChannel channel(String consumerName) throws IOException {
        FileChannel ret = channels.get(consumerName);
        if (ret == null) {
            Files.createDirectories(directory);
            ret = FileChannel.open(directory.resolve(consumerName),
                                   StandardOpenOption.CREATE,
                                   StandardOpenOption.READ,
                                   StandardOpenOption.WRITE);
            channels.put(consumerName, ret);
        }
        return ret;
    }
}
