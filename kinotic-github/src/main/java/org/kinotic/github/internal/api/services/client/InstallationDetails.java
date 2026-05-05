package org.kinotic.github.internal.api.services.client;

/**
 * Subset of GitHub's installation JSON the platform reads — the install id plus
 * the owning account's login + type ({@code User} or {@code Organization}).
 * Returned by {@link DefaultGitHubApiClient#getInstallation}.
 */
public record InstallationDetails(Long id, String accountLogin, String accountType) {}
