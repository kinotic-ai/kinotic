package org.kinotic.os.internal.api.services.github.client;

/**
 * Thrown by {@link DefaultGitHubApiClient} for any non-success response from GitHub. Carries
 * the operation name plus status code and response body in the message.
 */
public class GitHubApiException extends RuntimeException {
    public GitHubApiException(String message) {
        super(message);
    }
}
