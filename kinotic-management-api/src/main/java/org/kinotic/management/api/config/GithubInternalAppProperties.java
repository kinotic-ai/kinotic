package org.kinotic.management.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Kinotic's own GitHub App, installed only on Kinotic's repositories and separate from the App organizations
 * install, so work on Kinotic's repositories never widens what customers grant. It files the feedback
 * AI agents send as issues on {@code kinotic-ai/kinotic}. While {@link #appId} or {@link #appPrivateKey} is unset,
 * the server offers no {@code FeedbackService}, so agents see no way to send feedback.
 * <p>
 * In Kubernetes {@link #appPrivateKey} is a file of the server's system-secrets volume named after its
 * property, as {@link GithubProperties#getAppPrivateKey()} is.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class GithubInternalAppProperties {

    /** Numeric GitHub App id (from the App's settings page). */
    private String appId;

    /** RSA private-key PEM downloaded from the App's settings page, PKCS#1 or PKCS#8. */
    private String appPrivateKey;

}
