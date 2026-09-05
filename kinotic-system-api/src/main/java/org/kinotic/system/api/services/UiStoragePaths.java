package org.kinotic.system.api.services;

import org.apache.commons.lang3.Validate;

/**
 * The layout of published UIs inside an organization's {@code sites} container. Every path is
 * built here, so the environment segment exists in exactly one place.
 *
 * <pre>
 * prod/&lt;app&gt;/ui/&lt;ui&gt;/index.html          the site's entry, replaced last on each publish
 * prod/&lt;app&gt;/ui/&lt;ui&gt;/version.json        {@code { "commitSha": "..." }}
 * prod/&lt;app&gt;/ui/&lt;ui&gt;/assets/...           the build's hashed files, cached for a year
 * prod/&lt;app&gt;/ui/&lt;ui&gt;/...                  the rest of the build, never cached
 * </pre>
 *
 * Every blob is stamped with the commit that published it, so a publish can delete what
 * older commits left.
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

}
