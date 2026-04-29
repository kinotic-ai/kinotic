package org.kinotic.os.github.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Returned by {@code GitHubAppInstallationService.startInstall()}. The SPA navigates
 * the browser to {@link #url} ({@code window.location = url}); the embedded
 * {@code state} parameter is single-use and bound to the caller's organization in
 * a cluster-wide store, so the callback handler can resolve the org without any
 * session cookie or follow-up auth.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class InstallStartResponse {
    private String url;
}
