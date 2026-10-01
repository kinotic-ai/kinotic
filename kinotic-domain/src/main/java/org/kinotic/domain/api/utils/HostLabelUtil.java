package org.kinotic.domain.api.utils;

import org.kinotic.domain.api.model.ApplicationKey;

import java.net.URI;
import java.util.Locale;

/**
 * The hostname labels an application is served under: its API host {@code <organizationId>--<applicationId>}
 * and the site of each of its UIs, {@code <organizationId>--<applicationId>--<uiName>}. Organization,
 * application and UI names never contain the separator, so a label names exactly one application, and one UI.
 * The API host is a label under a base URL each server configures for itself, so the methods that read or
 * build a full host take that base URL from the caller.
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
     * {@code apiBaseUrl}, so {@code acme--orders.apps-api.kinotic.ai} under {@code https://apps-api.kinotic.ai}
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
     * {@code apiBaseUrl}, with that URL's scheme and port, so {@code https://acme--orders.apps-api.kinotic.ai}
     * under {@code https://apps-api.kinotic.ai}.
     *
     * @param apiBaseUrl the base URL every application's API host is a label under
     */
    public static String apiUrl(ApplicationKey applicationKey, String apiBaseUrl) {
        URI base = URI.create(apiBaseUrl);
        return base.getScheme() + "://" + label(applicationKey) + "." + base.getRawAuthority();
    }

    /** The label of the site of the application's UI {@code uiName}, {@code <organizationId>--<applicationId>--<uiName>}. */
    public static String siteLabel(ApplicationKey applicationKey, String uiName) {
        return label(applicationKey) + HOST_LABEL_SEPARATOR + uiName;
    }

    /** Whether {@code label} is the {@link #siteLabel(ApplicationKey, String) label of the site} of one of the application's UIs. */
    public static boolean isSiteLabel(ApplicationKey applicationKey, String label) {
        String[] names = label.split(HOST_LABEL_SEPARATOR, -1);
        return names.length == 3 && names[0].equals(applicationKey.organizationId()) && names[1].equals(applicationKey.applicationId())
                && !names[2].isEmpty();
    }
}
