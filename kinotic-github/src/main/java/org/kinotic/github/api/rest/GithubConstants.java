package org.kinotic.github.api.rest;

/**
 * Fixed paths used by the GitHub integration. Changing the webhook path requires
 * re-registering the App's webhook URL. The post-install callback is owned by the
 * SPA (which calls {@code completeInstall} via RPC) and lives in the frontend
 * routing config.
 */
public final class GithubConstants {

    private GithubConstants() {}

    /** Gateway route the GitHub App's webhook posts to. */
    public static final String WEBHOOK_PATH = "/api/github/webhook";
}
