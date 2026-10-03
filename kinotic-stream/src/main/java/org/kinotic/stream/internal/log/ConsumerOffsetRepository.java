package org.kinotic.stream.internal.log;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores, for each consumer of one stream, the next offset to deliver on every shard.
 */
public final class ConsumerOffsetRepository implements AutoCloseable {

    private final Path directory;
    private final int shardCount;
    private final ConcurrentHashMap<String, FileChannel> channels = new ConcurrentHashMap<>();

    ConsumerOffsetRepository(Path directory, int shardCount) {
        this.directory = directory;
        this.shardCount = shardCount;
    }

    /**
     * @return the next offset to deliver to the consumer, indexed by shard; zero on shards the consumer has never
     * committed on, since a commit always stores an offset of at least one
     */
    public long[] findNextOffsets(String consumerName) {
        long[] ret = new long[shardCount];
        Path file = directory.resolve(consumerName);
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
    }

    /**
     * Stores the next offset to deliver to the consumer on one shard.
     */
    public void save(String consumerName, int shard, long nextOffset) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).putLong(0, nextOffset);
        try {
            FileChannel channel = channel(consumerName);
            while (buffer.hasRemaining()) {
                channel.write(buffer, (long) shard * Long.BYTES + buffer.position());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        for (FileChannel channel : channels.values()) {
            try {
                channel.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private FileChannel channel(String consumerName) {
        return channels.computeIfAbsent(consumerName, name -> {
            try {
                Files.createDirectories(directory);
                return FileChannel.open(directory.resolve(name),
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.READ,
                                        StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
