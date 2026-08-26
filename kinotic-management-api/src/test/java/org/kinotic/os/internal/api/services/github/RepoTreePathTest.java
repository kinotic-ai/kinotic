package org.kinotic.os.internal.api.services.github;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepoTreePathTest {

    @Test
    void allowsContainedPaths() {
        assertEquals("a/b/c.txt", RepoTreePath.requireContained("a/b/c.txt"));
        // internal ../ that stays within the root is fine
        assertEquals("a/../b.txt", RepoTreePath.requireContained("a/../b.txt"));
    }

    @Test
    void rejectsTraversalAndAbsolutePaths() {
        assertThrows(IllegalArgumentException.class, () -> RepoTreePath.requireContained("../escape"));
        assertThrows(IllegalArgumentException.class, () -> RepoTreePath.requireContained("a/../../escape"));
        assertThrows(IllegalArgumentException.class, () -> RepoTreePath.requireContained("/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> RepoTreePath.requireContained(""));
    }
}
