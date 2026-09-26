/**
 * The software bill of materials of a Project: every package its bun.lock resolves, as a
 * CycloneDX document kept in the organization's storage. One row per project; the id equals the
 * project id. A deployment replaces it when the project's dependencies changed.
 */
export class ProjectSbom {

    /**
     * The id of the SBOM, always equal to the id of the project.
     */
    public id: string | null = null

    public organizationId!: string

    public applicationId!: string

    /**
     * Sha of the commit whose checkout the document was generated from. Later commits that leave
     * the project's dependencies unchanged keep the document.
     */
    public commitSha!: string

    /**
     * The ProjectArtifacts.dependencyHash the document was generated from: the SBOM is current for
     * a commit whose artifacts carry the same hash.
     */
    public dependencyHash!: string

    /**
     * When the document was generated.
     */
    public generated!: number

}
