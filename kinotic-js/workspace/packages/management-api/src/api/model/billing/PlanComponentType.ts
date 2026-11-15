/**
 * What a plan component charges for.
 */
export enum PlanComponentType {
    /** A fee each billing interval. */
    FLAT = 'FLAT',
    /** A price for each unit of a usage meter used. */
    METERED = 'METERED'
}
