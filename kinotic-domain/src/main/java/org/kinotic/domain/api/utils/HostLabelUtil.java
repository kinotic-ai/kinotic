package org.kinotic.domain.api.utils;

import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.SiteAddress;

import java.net.URI;
import java.util.Locale;

/**
 * The hostnames an application is served under. Its API host is the label {@code <organizationId>--<applicationId>}
 * under a base URL each server configures for itself, so the methods that read or build a full API host take that
 * base URL from the caller. Each of its UIs' sites is {@code <applicationId>--<uiName>} under the organization's own
 * subdomain of the sites domain, {@code <applicationId>--<uiName>.<organizationId>.<sites domain>}, and a site
 * addressed to one tenant of the application puts the tenant first,
 * {@code <tenantId>--<applicationId>--<uiName>.<organizationId>.<sites domain>}. Organization, application, UI and
 * tenant names never contain the separator, so a label names exactly one application, one UI and, when it carries
 * one, one tenant.
 */
public final class HostLabelUtil {

    /**
     * Separates the names a host label joins. No name contains it, so a label names exactly one application,
     * and one UI.
     */
    public static final String HOST_LABEL_SEPARATOR = "--";

    /** The longest label DNS allows, which bounds every host label the platform mints. */
    public static final int MAX_HOST_LABEL_LENGTH = 63;

    private HostLabelUtil() {
    }

    /** The label of the application's API host, {@code <organizationId>--<applicationId>}. */
    public static String label(ApplicationKey applicationKey) {
        return applicationKey.organizationId() + HOST_LABEL_SEPARATOR + applicationKey.applicationId();
    }

    /**
     * The application whose API host label is {@code label}, or {@code null} when {@code label} is not one DNS
     * label joining two names, {@code <organizationId>--<applicationId>}.
     */
    public static ApplicationKey fromLabel(String label) {
        String[] names = label.split(HOST_LABEL_SEPARATOR, -1);
        return label.indexOf('.') < 0 && names.length == 2 && !names[0].isEmpty() && !names[1].isEmpty()
                ? new ApplicationKey(names[0], names[1])
                : null;
    }

    /**
     * The application whose API host {@code host} is: a {@link #label(ApplicationKey) label} under the domain of
     * {@code apiBaseUrl}, so {@code acme--orders.apps-api.kinotic.io} under {@code https://apps-api.kinotic.io}
     * is application {@code orders} of organization {@code acme}.
     *
     * @param host       a hostname, as a request names it; compared ignoring case
     * @param apiBaseUrl the base URL every application's API host is a label under
     * @return the application, or {@code null} when {@code host} is no application's API host, including
     *         {@code apiBaseUrl}'s own host
     */
    public static ApplicationKey fromHost(String host, String apiBaseUrl) {
        String domainSuffix = "." + URI.create(apiBaseUrl).getHost().toLowerCase(Locale.ROOT);
        String name = host.toLowerCase(Locale.ROOT);
        ApplicationKey ret = null;
        if (name.endsWith(domainSuffix)) {
            ret = fromLabel(name.substring(0, name.length() - domainSuffix.length()));
        }
        return ret;
    }

    /**
     * The URL a browser reaches the application's API on: its {@link #label(ApplicationKey) label} under
     * {@code apiBaseUrl}, with that URL's scheme and port, so {@code https://acme--orders.apps-api.kinotic.io}
     * under {@code https://apps-api.kinotic.io}.
     *
     * @param apiBaseUrl the base URL every application's API host is a label under
     */
    public static String apiUrl(ApplicationKey applicationKey, String apiBaseUrl) {
        URI base = URI.create(apiBaseUrl);
        return base.getScheme() + "://" + label(applicationKey) + "." + base.getRawAuthority();
    }

    /** The label of the site of the application's UI {@code uiName} within the organization's subdomain, {@code <applicationId>--<uiName>}. */
    public static String siteLabel(ApplicationKey applicationKey, String uiName) {
        return applicationKey.applicationId() + HOST_LABEL_SEPARATOR + uiName;
    }

    /**
     * The id of the site of the application's UI {@code uiName}: its hostname under the sites domain,
     * {@code <applicationId>--<uiName>.<organizationId>}, which names one UI of one application of one organization.
     */
    public static String siteId(ApplicationKey applicationKey, String uiName) {
        return siteLabel(applicationKey, uiName) + "." + applicationKey.organizationId();
    }

    /**
     * What the site at {@code host} names, read from its first two labels: the organization from the second, and
     * from the first the application and UI, {@code <applicationId>--<uiName>}, with the tenant before them when it
     * has three names, {@code <tenantId>--<applicationId>--<uiName>}. {@code null} when {@code host} has no such
     * shape; whether the host is under the sites domain is the caller's to check.
     *
     * @param host a hostname, as a request or an origin names it; compared ignoring case
     */
    public static SiteAddress fromSiteHost(String host) {
        String[] labels = host.toLowerCase(Locale.ROOT).split("\\.", -1);
        SiteAddress ret = null;
        if (labels.length >= 3 && !labels[1].isEmpty()) {
            String[] names = labels[0].split(HOST_LABEL_SEPARATOR, -1);
            boolean named = names.length >= 2 && names.length <= 3;
            for (String name : names) {
                named &= !name.isEmpty();
            }
            if (named) {
                int app = names.length - 2;
                ret = new SiteAddress(new ApplicationKey(labels[1], names[app]), names[app + 1], names.length == 3 ? names[0] : null);
            }
        }
        return ret;
    }
}
