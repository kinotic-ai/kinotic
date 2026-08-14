export interface CrudHeader {
  field: string
  header: string
  sortable?: boolean
  centered?: boolean
  /** CSS width for the column, e.g. `'22%'` or `'8rem'`. Columns without one split the leftover width evenly. */
  width?: string
}
