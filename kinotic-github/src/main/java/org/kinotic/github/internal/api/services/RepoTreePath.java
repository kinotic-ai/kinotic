package org.kinotic.github.internal.api.services;

import java.nio.file.Path;

/**
 * Guards repo-relative paths originating from a template against directory
 * traversal. A path that resolves outside the repository root — via {@code ..}
 * or an absolute path, whether authored that way or produced by rendering a
 * property value into a path — is rejected. Internal {@code ../} that stays
 * within the root is allowed.
 */
final class RepoTreePath {

    private static final Path ROOT = Path.of("/__repo_root__");

    private RepoTreePath() {
    }

    static String requireContained(String path) {
        Path resolved = ROOT.resolve(path).normalize();
        if (resolved.equals(ROOT) || !resolved.startsWith(ROOT)) {
            throw new IllegalArgumentException("Template path escapes the repository root: " + path);
        }
        return path;
    }
}
