package org.kinotic.os.internal.api.services.github;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateTarballTest {

    @Test
    void parsesFilesStrippingTheRootDirectory() throws IOException {
        byte[] tarball = tarball(Map.of(
                "acme-demo-abc123/package.json.liquid", entry("{\"name\": \"{{projectName}}\"}", false),
                "acme-demo-abc123/gradlew", entry("#!/bin/sh\n", true),
                "acme-demo-abc123/assets/logo.png", entry(new byte[]{0, 1, 2}, false)));

        Map<String, TarballFile> files = TemplateTarball.parse(tarball);

        assertEquals("{\"name\": \"{{projectName}}\"}",
                     new String(files.get("package.json.liquid").content(), StandardCharsets.UTF_8));
        assertFalse(files.get("package.json.liquid").executable());
        assertTrue(files.get("gradlew").executable());
        assertArrayEquals(new byte[]{0, 1, 2}, files.get("assets/logo.png").content());
        assertEquals(3, files.size());
    }

    @Test
    void ignoresEntriesOutsideTheRootDirectory() throws IOException {
        byte[] tarball = tarball(Map.of(
                "pax_global_header", entry("ignored", false),
                "root/kept.txt", entry("kept", false)));

        Map<String, TarballFile> files = TemplateTarball.parse(tarball);

        assertEquals(1, files.size());
        assertTrue(files.containsKey("kept.txt"));
    }

    @Test
    void failsOnGarbageInput() {
        assertThrows(UncheckedIOException.class, () -> TemplateTarball.parse(new byte[]{1, 2, 3}));
    }

    @Test
    void rejectsAnEntryThatEscapesTheRoot() throws IOException {
        byte[] tarball = tarball(Map.of("root/../../etc/cron.d/evil", entry("*/1 * * * * root sh", false)));

        assertThrows(IllegalArgumentException.class, () -> TemplateTarball.parse(tarball));
    }

    private record Entry(byte[] content, boolean executable) {
    }

    private static Entry entry(String content, boolean executable) {
        return new Entry(content.getBytes(StandardCharsets.UTF_8), executable);
    }

    private static Entry entry(byte[] content, boolean executable) {
        return new Entry(content, executable);
    }

    private static byte[] tarball(Map<String, Entry> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(out))) {
            for (Map.Entry<String, Entry> e : entries.entrySet()) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(e.getKey());
                tarEntry.setSize(e.getValue().content().length);
                tarEntry.setMode(e.getValue().executable() ? 0100755 : 0100644);
                tar.putArchiveEntry(tarEntry);
                tar.write(e.getValue().content());
                tar.closeArchiveEntry();
            }
        }
        return out.toByteArray();
    }

}
