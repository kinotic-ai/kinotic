package org.kinotic.github.api.rest;

/**
 * Fixed paths used by the GitHub integration. All three are part of the platform's
 * external contract: changing them would require re-registering the GitHub App
 * (callback + webhook) or the SPA route (success).
 */
public final class GithubConstants {

    private GithubConstants() {}

    /** Gateway route the GitHub App's "Setup URL" / post-install redirect points at. */
    public static final String INSTALL_CALLBACK_PATH = "/api/github/install/callback";

    /** Gateway route the GitHub App's webhook posts to. */
    public static final String WEBHOOK_PATH = "/api/github/webhook";

    /** Frontend path the user is redirected to after a successful install. The new
     *  installation id is appended as {@code ?installationId=<id>}; on errors,
     *  {@code ?error=<code>} is appended instead. */
    public static final String INSTALL_SUCCESS_PATH = "/integrations/github";
}
