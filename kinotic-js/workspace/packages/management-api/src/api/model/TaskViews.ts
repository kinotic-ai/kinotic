/**
 * The kinds of task views the platform's jobs show, mirroring TaskViews on the server: each key
 * names the component that renders a TaskView of that kind, and the type of data it renders.
 */
export const TaskViews = {
    /** A workload's console log; data is a WorkloadLog. */
    WORKLOAD_LOG: 'workloadLog',
    /** The artifacts a deployed commit contains; data is ProjectArtifacts. */
    PROJECT_ARTIFACTS: 'projectArtifacts',
    /** The generation of a project's SBOM; data is SbomGeneration. */
    SBOM_GENERATION: 'sbomGeneration',
    /** What one import of an ecosystem's vulnerability advisories from OSV stored; data is AdvisoryImport. */
    ADVISORY_IMPORT: 'advisoryImport',
    /** What one scan of every project's SBOM for vulnerabilities did; data is SbomScan. */
    SBOM_SCAN: 'sbomScan'
} as const
