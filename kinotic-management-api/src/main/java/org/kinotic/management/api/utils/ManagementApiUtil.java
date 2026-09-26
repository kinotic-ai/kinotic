package org.kinotic.management.api.utils;

import org.apache.commons.lang3.Validate;

/**
 * Static helpers shared across the management plane and its consumers.
 */
public final class ManagementApiUtil {

    private ManagementApiUtil() {
    }

    /**
     * The path of a project's SBOM in the organization storage account's container:
     * {@code <organizationId>/sboms/<projectId>.cdx.json}. Everything the platform keeps for an
     * organization sits under the organization's directory, partitioned by use.
     */
    public static String projectSbomFile(String organizationId, String projectId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notBlank(projectId, "projectId cannot be blank");
        return organizationId + "/sboms/" + projectId + ".cdx.json";
    }

}
