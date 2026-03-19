import type { ExpansionStateManager } from './ExpansionStateManager'
import type { PropertyInspector } from './PropertyInspector'

interface HeaderDef {
  header: string
  field: string
  sortable: boolean
  width: number
  isCollapsable?: boolean
  expandedWidth?: number | null
  [key: string]: any
}

export class ColumnWidthCalculator {
  private expansion: ExpansionStateManager
  private inspector: PropertyInspector
  private headers: HeaderDef[] = []
  private items: any[] = []

  private _autoWidthCache: Map<string, number> = new Map()


  columnWidths: Map<string, number> = new Map()

  get nestedColumnWidths(): Map<string, number> { return this.columnWidths }
  set nestedColumnWidths(_v: Map<string, number>) { /* noop, use columnWidths */ }
  get deepNestedColumnWidths(): Map<string, number> { return this.columnWidths }
  set deepNestedColumnWidths(_v: Map<string, number>) { /* noop */ }
  get veryDeepNestedColumnWidths(): Map<string, number> { return this.columnWidths }
  set veryDeepNestedColumnWidths(_v: Map<string, number>) { /* noop */ }
  get ultraDeepNestedColumnWidths(): Map<string, number> { return this.columnWidths }
  set ultraDeepNestedColumnWidths(_v: Map<string, number>) { /* noop */ }

  constructor(expansion: ExpansionStateManager, inspector: PropertyInspector) {
    this.expansion = expansion
    this.inspector = inspector
  }

  setHeaders(headers: HeaderDef[]): void {
    this.headers = headers
  }

  setItems(items: any[]): void {
    this.items = items
    this._autoWidthCache.clear()
  }

  getWidthAtPath(...path: string[]): number {
    const key = path.join('.')

    if (path.length >= 2 && this.expansion.isPathExpanded(...path)) {
      const childProps = this.inspector.getPropertiesAtPath(...path)
      let totalWidth = 0
      for (const child of childProps) {
        const childPath = [...path, child.name]
        if ((child.isObject || child.isArray) && this.expansion.isPathExpanded(...childPath)) {
          totalWidth += this.getWidthAtPath(...childPath)
        } else {
          totalWidth += this.getLeafWidth(...childPath)
        }
      }
      return Math.max(totalWidth, childProps.length * 80, 100)
    }

    if (this.columnWidths.has(key)) {
      return this.columnWidths.get(key)!
    }

    return this.getLeafWidth(...path)
  }

  private getLeafWidth(...path: string[]): number {
    const key = path.join('.')
    if (this.columnWidths.has(key)) {
      return this.columnWidths.get(key)!
    }
    if (this._autoWidthCache.has(key)) {
      return this._autoWidthCache.get(key)!
    }

    const propName = path[path.length - 1]

    if (this.items && this.items.length > 0 && path.length > 0) {
      let maxLength = propName.length
      for (const item of this.items) {
        const value = this.getValueAtPath(item, path)
        if (value !== null && value !== undefined) {
          const strValue = Array.isArray(value) ? `[${value.length} items]` : String(value)
          maxLength = Math.max(maxLength, strValue.length)
        }
      }
      const width = Math.max(Math.min(maxLength * 9 + 40, 300), 100)
      this._autoWidthCache.set(key, width)
      return width
    }

    return Math.max(Math.min(propName.length * 9 + 40, 300), 100)
  }

  private getValueAtPath(item: any, path: string[]): any {
    let current = item
    for (let i = 0; i < path.length; i++) {
      if (current === null || current === undefined) return undefined
      if (Array.isArray(current)) {
        current = current.length > 0 ? current[0] : undefined
      }
      if (current === null || current === undefined) return undefined
      current = current[path[i]]
    }
    return current
  }

  getNestedPropWidth(fieldName: string, nestedProp: any): number {
    const path = [fieldName, nestedProp.name]

    if ((nestedProp.isObject || nestedProp.isArray) && this.expansion.isPathExpanded(...path)) {
      const totalWidth = this.getWidthAtPath(...path)
      const key = path.join('.')
      if (this.columnWidths.has(key)) {
        return Math.max(this.columnWidths.get(key)!, totalWidth)
      }
      return totalWidth
    }

    return this.getLeafWidth(...path)
  }

  getExpandedColumnWidth(fieldName: string): number {
    if (!this.expansion.isColumnExpanded(fieldName)) return 240

    const nestedProps = this.inspector.getPropertiesAtPath(fieldName)
    let totalWidth = 0

    for (const nestedProp of nestedProps) {
      totalWidth += this.getNestedPropWidth(fieldName, nestedProp)
    }

    const hasExpandedNestedColumns = nestedProps.some(p =>
      (p.isObject || p.isArray) && this.expansion.isPathExpanded(fieldName, p.name)
    )

    if (hasExpandedNestedColumns) {
      return Math.max(totalWidth, 120)
    }

    const header = this.headers.find(h => h.field === fieldName)
    const hasCustomWidths = nestedProps.some(p =>
      this.columnWidths.has(`${fieldName}.${p.name}`)
    )
    if (header?.expandedWidth && !hasCustomWidths) {
      return Math.max(header.expandedWidth, totalWidth)
    }

    return Math.max(totalWidth, 120)
  }

  getNestedObjectSubColumnWidth(fieldName: string, nestedProp: string, subPropName: string): number {
    return this.getLeafWidth(fieldName, nestedProp, subPropName)
  }

  getDeepNestedSubColumnWidth(fieldName: string, nestedProp: string, arrayProp: string, subPropName: string): number {
    return this.getLeafWidth(fieldName, nestedProp, arrayProp, subPropName)
  }

  getUltraDeepNestedSubColumnWidth(
    fieldName: string,
    nestedProp: string,
    arrayProp: string,
    subArrayProp: string,
    ultraDeepPropName: string
  ): number {
    return this.getLeafWidth(fieldName, nestedProp, arrayProp, subArrayProp, ultraDeepPropName)
  }

  getNestedPropRenderWidth(fieldName: string, nestedProp: any, nestedPropIndex: number): number {
    const baseWidth = this.getNestedPropWidth(fieldName, nestedProp)
    if (!this.expansion.isColumnExpanded(fieldName)) return baseWidth

    const nestedProps = this.inspector.getPropertiesAtPath(fieldName)
    if (!nestedProps || nestedProps.length === 0) return baseWidth

    const isLast = nestedPropIndex === nestedProps.length - 1
    if (!isLast) return baseWidth

    const total = this.getExpandedColumnWidth(fieldName)
    let used = 0
    for (let i = 0; i < nestedPropIndex; i++) {
      used += this.getNestedPropWidth(fieldName, nestedProps[i])
    }

    const remaining = total - used
    if (remaining <= 0) return baseWidth
    return Math.max(baseWidth, remaining)
  }
}
