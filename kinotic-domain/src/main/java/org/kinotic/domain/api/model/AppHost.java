package org.kinotic.domain.api.model;

/**
 * The hostname labels an application is served under: its API host {@code <organizationId>--<applicationId>}
 * and the site of each of its UIs, {@code <organizationId>--<applicationId>--<uiName>}. Organization,
 * application and UI names never contain {@code --}, so a label names exactly one application, and one UI.
 *
 * @param organizationId the organization that owns the application
 * @param applicationId  the application
 */
public record AppHost(String organizationId, String applicationId) {

    /** The longest label DNS allows. */
    public static final int MAX_LABEL_LENGTH = 63;

    /** Separates the names a label joins; no name contains it. */
    public static final String SEPARATOR = "--";

    /**
     * The application whose API host label is {@code label}, or {@code null} when {@code label} is not one DNS
     * label joining two names, {@code <organizationId>--<applicationId>}.
     */
    public static AppHost fromLabel(String label) {
        String[] names = label.split(SEPARATOR, -1);
        return label.indexOf('.') < 0 && names.length == 2 && !names[0].isEmpty() && !names[1].isEmpty()
                ? new AppHost(names[0], names[1])
                : null;
    }

    /** The label of the application's API host, {@code <organizationId>--<applicationId>}. */
    public String label() {
        return organizationId + SEPARATOR + applicationId;
    }

    /** The label of the site of the application's UI {@code uiName}, {@code <organizationId>--<applicationId>--<uiName>}. */
    public String siteLabel(String uiName) {
        return label() + SEPARATOR + uiName;
    }

    /** Whether {@code label} is the {@link #siteLabel(String) label of the site} of one of the application's UIs. */
    public boolean isSiteLabel(String label) {
        String[] names = label.split(SEPARATOR, -1);
        return names.length == 3 && names[0].equals(organizationId) && names[1].equals(applicationId)
                && !names[2].isEmpty();
    }
}
