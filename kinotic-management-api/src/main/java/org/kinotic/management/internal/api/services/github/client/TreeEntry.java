package org.kinotic.management.internal.api.services.github.client;

/**
 * One file in a Git tree to be created via
 * {@link GitHubApiClient#createTree}. Exactly one of {@code content} or
 * {@code sha} is set: inline UTF-8 text content, or the SHA of a blob
 * previously created with {@link GitHubApiClient#createBlob} for binary files.
 */
public record TreeEntry(String path, String mode, String content, String sha) {

    public static final String MODE_FILE = "100644";
    public static final String MODE_EXECUTABLE = "100755";

    public static TreeEntry text(String path, String mode, String content) {
        return new TreeEntry(path, mode, content, null);
    }

    public static TreeEntry blob(String path, String mode, String sha) {
        return new TreeEntry(path, mode, null, sha);
    }
}
