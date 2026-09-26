package org.kinotic.management.api.services.storage;

import org.apache.commons.lang3.Validate;

/**
 * The layout of the organization storage account. Every path is built here, so it exists in
 * exactly one place. Everything the platform keeps for an organization sits under the
 * organization's directory of the {@code organizations} container, partitioned by use:
 *
 * <pre>
 * organizations/&lt;organizationId&gt;/sboms/&lt;projectId&gt;.cdx.json   a project's SBOM
 * </pre>
 */
public final class OrganizationStoragePaths {

    /** The container of the organization storage account. */
    public static final String CONTAINER = "organizations";

    private OrganizationStoragePaths() {
    }

    /**
     * The SBOM of one project.
     */
    public static String sbomFile(String organizationId, String projectId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notBlank(projectId, "projectId cannot be blank");
        return organizationId + "/sboms/" + projectId + ".cdx.json";
    }

}
