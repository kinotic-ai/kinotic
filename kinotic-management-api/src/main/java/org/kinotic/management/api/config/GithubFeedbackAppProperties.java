package org.kinotic.management.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The GitHub App that files the feedback organizations send as issues on {@code kinotic-ai/kinotic}. It is
 * separate from the App organizations install, holds only the Issues permission and is installed only on
 * the Kinotic repository. While {@link #appId} is unset, the server offers no {@code FeedbackService}, so
 * neither the portal nor an AI agent sees a way to send feedback.
 * <p>
 * In Kubernetes {@link #appPrivateKey} is a file of the server's system-secrets volume named after its
 * property, as {@link GithubProperties#getAppPrivateKey()} is.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class GithubFeedbackAppProperties {

    /** Numeric GitHub App id (from the App's settings page). */
    private String appId;

    /** RSA private-key PEM downloaded from the App's settings page, PKCS#1 or PKCS#8. */
    private String appPrivateKey;

}
