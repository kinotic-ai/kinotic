package org.kinotic.management.internal.api.services.github.client;

/**
 * Subset of GitHub's repository JSON returned by
 * {@link DefaultGitHubApiClient#createRepoFromTemplate}. Carries the fields the
 * provisioner stamps onto the new {@code Project}.
 */
public record CreatedRepository(Long id, String fullName, String defaultBranch) {}
