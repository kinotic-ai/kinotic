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

    /** The label of the application's API host, {@code <organizationId>--<applicationId>}. */
    public String label() {
        return organizationId + SEPARATOR + applicationId;
    }

    /** The label of the site of the application's UI {@code uiName}, {@code <organizationId>--<applicationId>--<uiName>}. */
    public String siteLabel(String uiName) {
        return label() + SEPARATOR + uiName;
    }
}
