package org.kinotic.management.api.model.deployment;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.ApplicationScoped;
import org.kinotic.management.api.model.Project;

import java.util.Date;

/**
 * The software bill of materials of a {@link Project}: every package its {@code bun.lock}
 * resolves, as a CycloneDX document kept in the organization's storage. One row per project;
 * {@link #id} equals the project id. A deployment replaces it when the project's dependencies
 * changed.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ProjectSbom implements ApplicationScoped<String> {

    /**
     * The id of the SBOM, always equal to the id of the project.
     */
    private String id;

    private String organizationId;

    private String applicationId;

    /**
     * Sha of the commit whose checkout the document was generated from. Later commits that leave
     * the project's dependencies unchanged keep the document.
     */
    private String commitSha;

    /**
     * The {@link ProjectArtifacts#dependencyHash()} the document was generated from: the SBOM is
     * current for a commit whose artifacts carry the same hash.
     */
    private String dependencyHash;

    /**
     * How many components the document lists.
     */
    private int componentCount;

    /**
     * When the document was generated.
     */
    private Date generated;

}
