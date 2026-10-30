package org.kinotic.management.internal.api.services.template;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses a GitHub repository tarball (gzipped tar with all paths wrapped in a
 * single root directory) into a map of repo-relative path to file. A template repository is
 * small, so an archive holding more than {@link #MAX_FILES} files, a file larger than
 * {@link #MAX_FILE_BYTES}, or more than {@link #MAX_TOTAL_BYTES} of files in all is refused.
 */
public final class TemplateTarball {

    /** The largest file a template repository holds. */
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    /** The most file content a template repository holds in all. */
    public static final long MAX_TOTAL_BYTES = 128L * 1024 * 1024;
    /** The most files a template repository holds. */
    public static final int MAX_FILES = 10_000;

    private TemplateTarball() {
    }

    /**
     * @return regular files keyed by repo-relative path, in archive order
     * @throws UncheckedIOException when {@code tarGz} is not a readable gzipped tar
     * @throws IllegalArgumentException when the archive exceeds what a template repository holds
     */
    public static Map<String, TarballFile> parse(byte[] tarGz) {
        Map<String, TarballFile> files = new LinkedHashMap<>();
        long totalBytes = 0;
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new GzipCompressorInputStream(new ByteArrayInputStream(tarGz)))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isFile()) {
                    continue;
                }
                String path = stripRootDirectory(entry.getName());
                if (path == null) {
                    // pax_global_header and other entries outside the root directory
                    continue;
                }
                RepoTreePath.requireContained(path);
                if (files.size() == MAX_FILES) {
                    throw new IllegalArgumentException("The repository tarball holds more than " + MAX_FILES + " files");
                }
                // the entry's header size is as untrusted as its content, so the content itself is counted,
                // one byte past the cap at most
                byte[] content = tar.readNBytes(MAX_FILE_BYTES + 1);
                if (content.length > MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("The repository file " + path + " exceeds " + MAX_FILE_BYTES + " bytes");
                }
                totalBytes += content.length;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new IllegalArgumentException("The repository tarball holds more than " + MAX_TOTAL_BYTES + " bytes of files");
                }
                boolean executable = (entry.getMode() & 0100) != 0;
                files.put(path, new TarballFile(content, executable));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read repository tarball", e);
        }
        return files;
    }

    private static String stripRootDirectory(String entryName) {
        int slash = entryName.indexOf('/');
        if (slash < 0 || slash == entryName.length() - 1) {
            return null;
        }
        return entryName.substring(slash + 1);
    }

}
