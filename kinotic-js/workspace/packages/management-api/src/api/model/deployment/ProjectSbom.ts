/**
 * The software bill of materials of the dependencies a project's ProjectArtifacts list: every
 * package the bun.lock of their checkout resolves, as a CycloneDX document kept in the
 * organization's storage. A sync that reports other dependencies drops it, and the deployment
 * generates it again.
 */
export interface ProjectSbom {
    /**
     * How many components the document lists.
     */
    componentCount: number
    /**
     * When the document was generated.
     */
    generated: number
}
