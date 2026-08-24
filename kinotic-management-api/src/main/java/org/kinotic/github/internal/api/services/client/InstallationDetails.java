package org.kinotic.github.internal.api.services.client;

/**
 * Subset of GitHub's installation JSON the platform reads — the install id, the id
 * of the GitHub App it belongs to, plus the owning account's login + type
 * ({@code User} or {@code Organization}).
 * Returned by {@link DefaultGitHubApiClient#listUserInstallations}.
 */
public record InstallationDetails(Long id, Long appId, String accountLogin, String accountType) {}
