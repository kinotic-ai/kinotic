/** One chip of a StatusChips row: a state and how many items are in it; a null value stands for every state. */
export interface StatusChip {
    label: string
    value: string | null
    count: number
    /** The state's status, as a Tag severity, which colours its dot; the All chip has none. */
    severity?: string
}
