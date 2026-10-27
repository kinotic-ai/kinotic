/**
 * The versions a CLI release runs its local stack at, bundled beside the compose files when the CLI is built.
 */
export interface LocalStackVersions {
    /** The tag of every Kinotic image the stack runs, the platform's version. */
    kinoticVersion: string
    /** The @kinotic-ai/vm-manager release that serves as the local node. */
    vmManagerVersion: string
}
