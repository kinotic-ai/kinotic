
export class ExpansionStateManager {
  expandedColumns: Set<string> = new Set()
  expandedRows: Map<string, Set<string>> = new Map()
  expandedNestedArrays: Map<string, Map<string, Set<string>>> = new Map()


  private _expandedPaths: Set<string> = new Set()

  get expandedNestedObjects(): Set<string> { return this._expandedPaths }
  get expandedDeepNested(): Set<string> { return this._expandedPaths }
  get expandedVeryDeepNested(): Set<string> { return this._expandedPaths }

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


  togglePathExpansion(...path: string[]): void {
    const key = path.join('.')
    if (this._expandedPaths.has(key)) {
      this._expandedPaths.delete(key)
    } else {
      this._expandedPaths.add(key)
    }
  }

  isPathExpanded(...path: string[]): boolean {
    return this._expandedPaths.has(path.join('.'))
  }

  // ---- Legacy methods that delegate to path-based ----

  toggleNestedObjectExpansion(fieldName: string, nestedProp: string): void {
    this.togglePathExpansion(fieldName, nestedProp)
  }

  isNestedObjectExpanded(fieldName: string, nestedProp: string): boolean {
    return this.isPathExpanded(fieldName, nestedProp)
  }

  toggleDeepNestedExpansion(fieldName: string, nestedProp: string, deepProp: string): void {
    this.togglePathExpansion(fieldName, nestedProp, deepProp)
  }

  isDeepNestedExpanded(fieldName: string, nestedProp: string, deepProp: string): boolean {
    return this.isPathExpanded(fieldName, nestedProp, deepProp)
  }

  toggleVeryDeepNestedExpansion(fieldName: string, nestedProp: string, deepProp: string, ...veryDeepPath: string[]): void {
    this.togglePathExpansion(fieldName, nestedProp, deepProp, ...veryDeepPath)
  }

  isVeryDeepNestedExpanded(fieldName: string, nestedProp: string, deepProp: string, ...veryDeepPath: string[]): boolean {
    return this.isPathExpanded(fieldName, nestedProp, deepProp, ...veryDeepPath)
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
