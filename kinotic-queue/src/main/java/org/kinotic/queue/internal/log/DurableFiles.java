package org.kinotic.queue.internal.log;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Reads and writes the small files queue data keeps beside its entries, so that a write survives a crash or power
 * loss once it returns.
 */
final class DurableFiles {

    private DurableFiles() {
    }

    /**
     * Forces a file or directory to disk. On Linux a file's fsync also writes the pages changed through a memory
     * mapping of it.
     */
    static void force(Path path) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Replaces the file's content so that, after a crash or power loss, the file holds either its old content or
     * {@code content}, never part of it.
     */
    static void writeDurably(Path file, String content) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            createDirectories(file.getParent());
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                                                        StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            force(file.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates the directory and any missing parent, each of which survives a crash or power loss once this returns.
     */
    static void createDirectories(Path directory) {
        if (!Files.isDirectory(directory)) {
            createDirectories(directory.getParent());
            try {
                Files.createDirectory(directory);
            } catch (FileAlreadyExistsException e) {
                // Created concurrently; forcing the parent below still makes it durable
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            force(directory.getParent());
        }
    }

    /**
     * @return the file's content without surrounding whitespace, or null when the file does not exist
     */
    static String read(Path file) {
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
     * @return the file's lines, or none when the file does not exist
     */
    static List<String> readLines(Path file) {
        List<String> ret = List.of();
        if (Files.exists(file)) {
            try {
                ret = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return ret;
    }
}
