/**
 * The software bill of materials of a project: every package its bun.lock resolves, as a
 * CycloneDX document kept in the organization's storage. A deployment replaces it when the
 * project's dependencies changed.
 */
export interface ProjectSbom {
    /**
     * Full 40-character SHA of the commit whose checkout the document was generated from. Later
     * commits that leave the dependencies unchanged keep the document.
     */
    commitSha: string
    /**
     * The ProjectArtifacts.dependencyHash the document was generated from: the SBOM is current for
     * a commit whose artifacts carry the same hash.
     */
    dependencyHash: string
    /**
     * How many components the document lists.
     */
    componentCount: number
    /**
     * When the document was generated.
     */
    generated: number
}
