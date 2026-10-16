/**
 * A deployment run's SBOM step: the workload generating the SBOM, and whether the run generated it.
 */
export interface SbomGeneration {

    /**
     * The id of the workload that generates the SBOM.
     */
    readonly workloadId: string

    /**
     * True when the run generated the SBOM, false when the dependencies were unchanged, null while
     * the step runs.
     */
    readonly generated: boolean | null

}
