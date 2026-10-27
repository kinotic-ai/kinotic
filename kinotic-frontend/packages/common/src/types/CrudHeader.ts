export interface CrudHeader {
  field: string
  header: string
  sortable?: boolean
  centered?: boolean
  /**
   * CSS width for the column, e.g. `'22%'` or `'8rem'`. Columns without one split the leftover width evenly.
   * When every column has a percentage, they are scaled to fill the table width together.
   */
  width?: string
  /** Hidden on small screens, so the columns that identify a row keep the width. */
  optional?: boolean
}
