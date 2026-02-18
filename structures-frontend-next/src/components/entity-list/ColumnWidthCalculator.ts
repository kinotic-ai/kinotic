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

  private _autoNestedObjectSubColumnWidths: Map<string, number> = new Map()
  private _autoDeepNestedSubColumnWidths: Map<string, number> = new Map()

  nestedColumnWidths: Map<string, number> = new Map()
  deepNestedColumnWidths: Map<string, number> = new Map()
  veryDeepNestedColumnWidths: Map<string, number> = new Map()
  ultraDeepNestedColumnWidths: Map<string, number> = new Map()

  constructor(expansion: ExpansionStateManager, inspector: PropertyInspector) {
    this.expansion = expansion
    this.inspector = inspector
  }

  setHeaders(headers: HeaderDef[]): void {
    this.headers = headers
  }

  setItems(items: any[]): void {
    this.items = items
    this._autoNestedObjectSubColumnWidths.clear()
    this._autoDeepNestedSubColumnWidths.clear()
  }

  getNestedPropWidth(fieldName: string, nestedProp: any): number {
    const key = `${fieldName}.${nestedProp.name}`

    if ((nestedProp.isObject || nestedProp.isArray) && this.expansion.isNestedObjectExpanded(fieldName, nestedProp.name)) {
      const subProps = this.inspector.getNestedObjectProperties(fieldName, nestedProp.name)
      let totalWidth = 0
      for (const subProp of subProps) {
        if (subProp.type?.type === 'array' && this.expansion.isDeepNestedExpanded(fieldName, nestedProp.name, subProp.name)) {
          const deepProps = this.inspector.getDeepNestedProperties(fieldName, nestedProp.name, subProp.name)
          for (const deepProp of deepProps) {
            if (deepProp.type?.type === 'array' && this.expansion.isVeryDeepNestedExpanded(fieldName, nestedProp.name, subProp.name, deepProp.name)) {
              const veryDeepProps = this.inspector.getVeryDeepNestedProperties(fieldName, nestedProp.name, subProp.name, deepProp.name)
              for (const vdp of veryDeepProps) {
                totalWidth += this.getUltraDeepNestedSubColumnWidth(fieldName, nestedProp.name, subProp.name, deepProp.name, vdp.name)
              }
            } else {
              totalWidth += this.getDeepNestedSubColumnWidth(fieldName, nestedProp.name, subProp.name, deepProp.name)
            }
          }
        } else {
          totalWidth += this.getNestedObjectSubColumnWidth(fieldName, nestedProp.name, subProp.name)
        }
      }
      const minWidthForAllColumns = Math.max(totalWidth, subProps.length * 80)

      if (this.nestedColumnWidths.has(key)) {
        const manualWidth = this.nestedColumnWidths.get(key)!
        return Math.max(manualWidth, minWidthForAllColumns)
      }

      return minWidthForAllColumns
    }

    if (this.nestedColumnWidths.has(key)) {
      return this.nestedColumnWidths.get(key)!
    }

    if (nestedProp.name === 'name') return 220
    if (nestedProp.name === 'sku') return 120
    if (nestedProp.name === 'productId') return 80
    if (['street', 'city', 'state'].includes(nestedProp.name)) return 150
    if (['postalCode', 'country'].includes(nestedProp.name)) return 120
    return 100
  }

  getExpandedColumnWidth(fieldName: string): number {
    if (!this.expansion.isColumnExpanded(fieldName)) return 240

    const nestedProps = this.inspector.getNestedProperties(fieldName)
    let totalWidth = 0

    for (const nestedProp of nestedProps) {
      totalWidth += this.getNestedPropWidth(fieldName, nestedProp)
    }

    const hasNestedCustomWidths = nestedProps.some(p =>
      this.nestedColumnWidths.has(`${fieldName}.${p.name}`)
    )

    const hasExpandedNestedColumns = nestedProps.some(p =>
      (p.isObject || p.isArray) && this.expansion.isNestedObjectExpanded(fieldName, p.name)
    )

    if (hasExpandedNestedColumns) {
      return Math.max(totalWidth, 120)
    }

    const header = this.headers.find(h => h.field === fieldName)
    if (header?.expandedWidth && !hasNestedCustomWidths) {
      return Math.max(header.expandedWidth, totalWidth)
    }

    return Math.max(totalWidth, 120)
  }

  getNestedObjectSubColumnWidth(fieldName: string, nestedProp: string, subPropName: string): number {
    const key = `${fieldName}.${nestedProp}.${subPropName}`
    if (this.deepNestedColumnWidths.has(key)) {
      return this.deepNestedColumnWidths.get(key)!
    }

    if (this._autoNestedObjectSubColumnWidths.has(key)) {
      return this._autoNestedObjectSubColumnWidths.get(key)!
    }

    if (!this.items || this.items.length === 0) return 80

    let maxLength = subPropName.length

    for (const item of this.items) {
      const nestedValue = item[fieldName]?.[nestedProp]
      let value

      if (Array.isArray(nestedValue) && nestedValue.length > 0) {
        value = nestedValue[0]?.[subPropName]
      } else {
        value = nestedValue?.[subPropName]
      }

      if (value !== null && value !== undefined) {
        const strValue = String(value)
        maxLength = Math.max(maxLength, strValue.length)
      }
    }

    const width = Math.max(Math.min(maxLength * 9 + 40, 300), 100)
    this._autoNestedObjectSubColumnWidths.set(key, width)
    return width
  }

  getDeepNestedSubColumnWidth(fieldName: string, nestedProp: string, arrayProp: string, subPropName: string): number {
    const key = `${fieldName}.${nestedProp}.${arrayProp}.${subPropName}`
    if (this.veryDeepNestedColumnWidths.has(key)) {
      return this.veryDeepNestedColumnWidths.get(key)!
    }

    if (this._autoDeepNestedSubColumnWidths.has(key)) {
      return this._autoDeepNestedSubColumnWidths.get(key)!
    }

    if (!this.items || this.items.length === 0) return 80

    let maxLength = subPropName.length

    for (const item of this.items) {
      const nestedArray = item[fieldName]?.[nestedProp]
      if (Array.isArray(nestedArray) && nestedArray.length > 0) {
        const firstNested = nestedArray[0]
        const deepArray = firstNested?.[arrayProp]
        if (Array.isArray(deepArray)) {
          for (const deepItem of deepArray) {
            const value = deepItem?.[subPropName]
            if (value !== null && value !== undefined) {
              const strValue = Array.isArray(value) ? `[${value.length} items]` : String(value)
              maxLength = Math.max(maxLength, strValue.length)
            }
          }
        }
      }
    }

    const width = Math.max(Math.min(maxLength * 9 + 40, 300), 100)
    this._autoDeepNestedSubColumnWidths.set(key, width)
    return width
  }

  getUltraDeepNestedSubColumnWidth(
    fieldName: string,
    nestedProp: string,
    arrayProp: string,
    subArrayProp: string,
    ultraDeepPropName: string
  ): number {
    const key = `${fieldName}.${nestedProp}.${arrayProp}.${subArrayProp}.${ultraDeepPropName}`
    if (this.ultraDeepNestedColumnWidths.has(key)) {
      return this.ultraDeepNestedColumnWidths.get(key)!
    }

    return Math.max(Math.min(ultraDeepPropName.length * 9 + 32, 360), 100)
  }

  getNestedPropRenderWidth(fieldName: string, nestedProp: any, nestedPropIndex: number): number {
    const baseWidth = this.getNestedPropWidth(fieldName, nestedProp)
    if (!this.expansion.isColumnExpanded(fieldName)) return baseWidth

    const nestedProps = this.inspector.getNestedProperties(fieldName)
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
