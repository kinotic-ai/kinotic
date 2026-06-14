package org.kinotic.github.internal.api.services;

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
 * single root directory) into a map of repo-relative path to file.
 */
public final class TemplateTarball {

    private TemplateTarball() {
    }

    /**
     * @return regular files keyed by repo-relative path, in archive order
     * @throws UncheckedIOException when {@code tarGz} is not a readable gzipped tar
     */
    public static Map<String, TarballFile> parse(byte[] tarGz) {
        Map<String, TarballFile> files = new LinkedHashMap<>();
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
                boolean executable = (entry.getMode() & 0100) != 0;
                files.put(path, new TarballFile(tar.readAllBytes(), executable));
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
