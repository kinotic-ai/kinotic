package org.kinotic.system.internal.utils;

import org.apache.commons.lang3.Validate;

/**
 * Static helpers used within the system plane.
 */
public final class SystemApiUtil {

    private SystemApiUtil() {
    }

    /**
     * The directory of one site in the {@code sites} container of the sites account: its
     * hostname, which is what the Front Door rule set derives from a request's host. A publish
     * lays the site's files out under it:
     *
     * <pre>
     * sites/&lt;hostname&gt;/index.html          the site's entry, replaced last on each publish
     * sites/&lt;hostname&gt;/version.json        {@code { "commitSha": "..." }}
     * sites/&lt;hostname&gt;/assets/...           the build's hashed files, cached for a year
     * sites/&lt;hostname&gt;/...                  the rest of the build, never cached
     * </pre>
     *
     * Every blob is stamped with the commit that published it, so a publish can delete what
     * older commits left.
     */
    public static String siteDirectory(String hostname) {
        Validate.notBlank(hostname, "hostname cannot be blank");
        return hostname;
    }

}
