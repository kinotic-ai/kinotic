/** One chip of a StatusChips row: a state and how many items are in it; a null value stands for every state. */
export interface StatusChip {
  label: string
  value: string | null
  count: number
}
