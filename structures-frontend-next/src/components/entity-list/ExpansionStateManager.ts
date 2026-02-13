
export class ExpansionStateManager {
  expandedColumns: Set<string> = new Set()
  expandedRows: Map<string, Set<string>> = new Map()
  expandedNestedObjects: Set<string> = new Set()
  expandedDeepNested: Set<string> = new Set()
  expandedVeryDeepNested: Set<string> = new Set()
  expandedNestedArrays: Map<string, Map<string, Set<string>>> = new Map()

  toggleColumnExpansion(fieldName: string): void {
    if (this.expandedColumns.has(fieldName)) {
      this.expandedColumns.delete(fieldName)
    } else {
      this.expandedColumns.add(fieldName)
    }
  }

  isColumnExpanded(fieldName: string): boolean {
    return this.expandedColumns.has(fieldName)
  }

  toggleRowExpansion(rowId: string, fieldName: string): void {
    if (!this.expandedRows.has(rowId)) {
      this.expandedRows.set(rowId, new Set())
    }
    const rowExpansions = this.expandedRows.get(rowId)!
    if (rowExpansions.has(fieldName)) {
      rowExpansions.delete(fieldName)
    } else {
      rowExpansions.add(fieldName)
    }
  }

  isRowCellExpanded(rowId: string, fieldName: string): boolean {
    return this.expandedRows.get(rowId)?.has(fieldName) || false
  }

  toggleNestedObjectExpansion(fieldName: string, nestedProp: string): void {
    const key = `${fieldName}.${nestedProp}`
    if (this.expandedNestedObjects.has(key)) {
      this.expandedNestedObjects.delete(key)
    } else {
      this.expandedNestedObjects.add(key)
    }
  }

  isNestedObjectExpanded(fieldName: string, nestedProp: string): boolean {
    return this.expandedNestedObjects.has(`${fieldName}.${nestedProp}`)
  }

  toggleDeepNestedExpansion(fieldName: string, nestedProp: string, deepProp: string): void {
    const key = `${fieldName}.${nestedProp}.${deepProp}`
    if (this.expandedDeepNested.has(key)) {
      this.expandedDeepNested.delete(key)
    } else {
      this.expandedDeepNested.add(key)
    }
  }

  isDeepNestedExpanded(fieldName: string, nestedProp: string, deepProp: string): boolean {
    return this.expandedDeepNested.has(`${fieldName}.${nestedProp}.${deepProp}`)
  }

  toggleVeryDeepNestedExpansion(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string): void {
    const key = `${fieldName}.${nestedProp}.${deepProp}.${veryDeepProp}`
    if (this.expandedVeryDeepNested.has(key)) {
      this.expandedVeryDeepNested.delete(key)
    } else {
      this.expandedVeryDeepNested.add(key)
    }
  }

  isVeryDeepNestedExpanded(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string): boolean {
    return this.expandedVeryDeepNested.has(`${fieldName}.${nestedProp}.${deepProp}.${veryDeepProp}`)
  }

  toggleNestedArrayExpansion(rowId: string, parentPath: string, arrayField: string): void {
    if (!this.expandedNestedArrays.has(rowId)) {
      this.expandedNestedArrays.set(rowId, new Map())
    }
    const rowMap = this.expandedNestedArrays.get(rowId)!
    if (!rowMap.has(parentPath)) {
      rowMap.set(parentPath, new Set())
    }
    const fieldSet = rowMap.get(parentPath)!
    if (fieldSet.has(arrayField)) {
      fieldSet.delete(arrayField)
    } else {
      fieldSet.add(arrayField)
    }
  }

  isNestedArrayExpanded(rowId: string, parentPath: string, arrayField: string): boolean {
    return this.expandedNestedArrays.get(rowId)?.get(parentPath)?.has(arrayField) || false
  }
}
