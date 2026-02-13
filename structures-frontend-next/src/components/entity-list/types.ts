/**
 * Shared type for the EntityList parent context.
 * Used by child components (EntityTableHeaders, EntityTableBody) via provide/inject
 * to access the parent's reactive state and methods without prop drilling.
 */
export interface EntityListContext {
  // Data
  headers: any[]
  items: any[]
  rowColors: any[]

  // Expansion state checks
  isColumnExpanded(fieldName: string): boolean
  isRowCellExpanded(rowId: string, fieldName: string): boolean
  isNestedObjectExpanded(fieldName: string, nestedProp: string): boolean
  isDeepNestedExpanded(fieldName: string, nestedProp: string, deepProp: string): boolean
  isVeryDeepNestedExpanded(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string): boolean
  isNestedArrayExpanded(rowId: string, parentPath: string, arrayField: string): boolean

  // Expansion state toggles
  toggleColumnExpansion(fieldName: string): void
  toggleRowExpansion(rowId: string, fieldName: string): void
  toggleNestedObjectExpansion(fieldName: string, nestedProp: string): void
  toggleDeepNestedExpansion(fieldName: string, nestedProp: string, deepProp: string): void
  toggleVeryDeepNestedExpansion(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string): void
  toggleNestedArrayExpansion(rowId: string, parentPath: string, arrayField: string): void

  // Property inspection
  isPrimitiveArray(fieldName: string): boolean
  isNestedPrimitiveArray(fieldName: string, nestedPropName: string): boolean
  isDeepNestedPrimitiveArray(fieldName: string, nestedProp: string, deepProp: string): boolean
  getNestedProperties(fieldName: string): any[]
  getNestedObjectProperties(fieldName: string, nestedProp: string): any[]
  getDeepNestedProperties(fieldName: string, nestedProp: string, deepProp: string): any[]
  getVeryDeepNestedProperties(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string): any[]

  // Width calculations
  getExpandedColumnWidth(fieldName: string): number
  getNestedPropWidth(fieldName: string, nestedProp: any): number
  getNestedPropRenderWidth(fieldName: string, nestedProp: any, nestedPropIndex: number): number
  getNestedObjectSubColumnWidth(fieldName: string, nestedProp: string, subPropName: string): number
  getDeepNestedSubColumnWidth(fieldName: string, nestedProp: string, arrayProp: string, subPropName: string): number
  getUltraDeepNestedSubColumnWidth(fieldName: string, nestedProp: string, arrayProp: string, subArrayProp: string, ultraDeepPropName: string): number

  startColumnResize(event: MouseEvent, header: any): void
  startNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: any): void
  startDeepNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: string, deepProp: any): void
  startVeryDeepNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: string, deepProp: string, veryDeepProp: any): void
  startUltraDeepNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: string, deepProp: string, veryDeepProp: string, ultraDeepProp: any): void

  getCellDisplayValue(data: any, field: string): string
  getCellTitleValue(data: any, field: string): string
  getArrayObjectLabel(obj: any, fieldName?: string): string
  getArrayPrimaryNestedProp(fieldName: string): string | null
  formatDate(date: string): string
  isDateField(field: string): boolean
}

export const ENTITY_LIST_INJECTION_KEY = 'entityListContext'
