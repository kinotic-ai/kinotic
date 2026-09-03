package org.kinotic.management.api.services;

import org.apache.commons.lang3.Validate;

/**
 * The layout of published UIs inside an organization's {@code ui} container. Every path is
 * built here, so the environment segment exists in exactly one place.
 *
 * <pre>
 * prod/&lt;app&gt;/ui/&lt;ui&gt;/index.html          the site's entry, replaced last on each publish
 * prod/&lt;app&gt;/ui/&lt;ui&gt;/version.json        {@code { "commitSha": "..." }}
 * prod/&lt;app&gt;/ui/&lt;ui&gt;/&lt;sha&gt;/...            the commit's immutable assets
 * </pre>
 */
public final class UiStoragePaths {

    private static final String ENVIRONMENT = "prod";

    private UiStoragePaths() {
    }

    /**
     * The prefix every UI of the application is published under; a publish workload uploads
     * relative to it.
     */
    public static String applicationPrefix(String applicationId) {
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        return ENVIRONMENT + "/" + applicationId + "/ui";
    }

    /**
     * The prefix one UI is published under: the site's root.
     */
    public static String uiPrefix(String applicationId, String uiName) {
        Validate.notBlank(uiName, "uiName cannot be blank");
        return applicationPrefix(applicationId) + "/" + uiName;
    }

    /**
     * The prefix one commit's immutable assets are published under.
     */
    public static String commitPrefix(String applicationId, String uiName, String commitSha) {
        Validate.notBlank(commitSha, "commitSha cannot be blank");
        return uiPrefix(applicationId, uiName) + "/" + commitSha;
    }

}
