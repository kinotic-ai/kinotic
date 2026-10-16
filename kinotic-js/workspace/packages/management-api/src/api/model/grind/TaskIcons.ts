/**
 * The icon keys a task or a nested job definition can declare, mirroring TaskIcons on the server.
 * A key a console does not know shows its default icon.
 */
export const TaskIcons = {
    /** Choosing where the work runs. */
    TARGET: 'target',
    /** Source checked out from a repository. */
    GIT: 'git',
    /** Built artifacts. */
    PACKAGE: 'package',
    /** Server workloads. */
    SERVER: 'server',
    /** Sites published to the web. */
    GLOBE: 'globe',
    /** A software bill of materials. */
    SBOM: 'sbom'
} as const
