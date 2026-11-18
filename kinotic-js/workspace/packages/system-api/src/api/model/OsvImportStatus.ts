import type { PackageEcosystem } from '@kinotic-ai/management-api'

/**
 * How far the advisories of one PackageEcosystem have been read from OSV, and how many of them are
 * stored.
 */
export interface OsvImportStatus {

    ecosystem: PackageEcosystem

    /**
     * The newest modification time of an advisory read; every advisory OSV changed up to it is stored
     * as OSV holds it. Null until the ecosystem's first import completes.
     */
    syncedThrough: string | null

    /**
     * How many advisories naming a package of the ecosystem are stored and not withdrawn.
     */
    advisoryCount: number

}
