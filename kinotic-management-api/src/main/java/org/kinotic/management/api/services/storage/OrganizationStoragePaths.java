package org.kinotic.management.api.services.storage;

import org.apache.commons.lang3.Validate;

/**
 * The layout of the organization storage account. Every path is built here, so it exists in
 * exactly one place. Everything the platform keeps for an organization sits under the
 * organization's directory of the {@code organizations} container, partitioned by use:
 *
 * <pre>
 * organizations/&lt;organizationId&gt;/sboms/&lt;projectId&gt;/&lt;commitSha&gt;.cdx.json   a project's SBOM, named by the commit it was generated from
 * </pre>
 */
public final class OrganizationStoragePaths {

    /** The container of the organization storage account. */
    public static final String CONTAINER = "organizations";

    private OrganizationStoragePaths() {
    }

    /**
     * The directory of one project's SBOMs.
     */
    public static String sbomDirectory(String organizationId, String projectId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notBlank(projectId, "projectId cannot be blank");
        return organizationId + "/sboms/" + projectId;
    }

    /**
     * The name, within its directory, of the SBOM generated from the given commit.
     */
    public static String sbomFileName(String commitSha) {
        Validate.notBlank(commitSha, "commitSha cannot be blank");
        return commitSha + ".cdx.json";
    }

    /**
     * The SBOM of one project generated from the given commit.
     */
    public static String sbomFile(String organizationId, String projectId, String commitSha) {
        return sbomDirectory(organizationId, projectId) + "/" + sbomFileName(commitSha);
    }

}
