package org.kinotic.os.internal.api.services.github;

/**
 * One regular file extracted from a repository tarball by
 * {@link TemplateTarball#parse}, keyed externally by its repo-relative path.
 */
public record TarballFile(byte[] content, boolean executable) {
}
