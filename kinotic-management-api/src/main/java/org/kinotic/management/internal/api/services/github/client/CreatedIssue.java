package org.kinotic.management.internal.api.services.github.client;

/**
 * Subset of GitHub's issue JSON returned by {@link DefaultGitHubApiClient#createIssue}.
 */
public record CreatedIssue(int number, String htmlUrl) {}
