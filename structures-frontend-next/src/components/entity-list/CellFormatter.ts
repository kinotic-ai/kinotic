import type { PropertyInspector } from './PropertyInspector'

export class CellFormatter {
  private inspector: PropertyInspector

  constructor(inspector: PropertyInspector) {
    this.inspector = inspector
  }

  getCellDisplayValue(data: any, field: string): string {
    const value = data[field]
    if (value === null || value === undefined) return 'null'

    if (Array.isArray(value)) {
      if (value.length === 0) return '[]'
      const firstItem = value[0]
      if (typeof firstItem !== 'object') {
        return value.length > 1 ? `${firstItem} +${value.length - 1}` : String(firstItem)
      }

      if (this.isKeyValueObject(firstItem) && firstItem.key !== undefined) {
        const firstKey = this.safeToString(firstItem.key)
        return value.length > 1 ? `${firstKey} +${value.length - 1}` : firstKey
      }

      const itemName = firstItem?.name || firstItem?.title || firstItem?.id
      if (itemName) {
        return value.length > 1 ? `${itemName} +${value.length - 1}` : itemName
      }
      const keys = Object.keys(firstItem)
      if (keys.length > 0) {
        const firstValue = firstItem[keys[0]]
        return value.length > 1 ? `${firstValue} +${value.length - 1}` : String(firstValue)
      }
      return value.length > 1 ? `Item +${value.length - 1}` : 'Item'
    } else if (typeof value === 'object') {
      const keys = Object.keys(value)
      if (keys.length === 0) return '{}'

      for (const key of keys) {
        const propValue = value[key]
        if (Array.isArray(propValue) && propValue.length > 0) {
          const firstItem = propValue[0]
          if (typeof firstItem === 'object' && firstItem !== null) {
            const itemKeys = Object.keys(firstItem)
            if (itemKeys.length > 0) {
              return String(firstItem[itemKeys[0]] ?? 'Item')
            }
          }
        }
      }

      const firstKey = keys[0]
      const firstValue = value[firstKey]

      if (typeof firstValue === 'object' && firstValue !== null && !Array.isArray(firstValue)) {
        const nestedKeys = Object.keys(firstValue)
        if (nestedKeys.length > 0) {
          return String(firstValue[nestedKeys[0]] || nestedKeys[0])
        }
      }

      return String(firstValue || firstKey)
    }

    return String(value)
  }

  getCellTitleValue(data: any, field: string): string {
    const value = data?.[field]
    if (value === null || value === undefined) return ''

    if (Array.isArray(value)) {
      if (value.length === 0) return '[]'
      const labels = value
        .slice(0, 12)
        .map((v) => (typeof v === 'object' ? this.getArrayObjectLabel(v, field) : this.safeToString(v)))
        .filter((s) => s.length > 0)
      const suffix = value.length > 12 ? ` …(+${value.length - 12})` : ''
      return labels.join(', ') + suffix
    }

    if (typeof value === 'object') {
      try {
        return JSON.stringify(value)
      } catch {
        return String(value)
      }
    }

    return this.safeToString(value)
  }

  getArrayPrimaryNestedProp(fieldName: string): string | null {
    const nestedProps = this.inspector.getNestedProperties(fieldName)
    if (!nestedProps || nestedProps.length === 0) return null

    const preferred = ['name', 'key', 'title', 'type', 'id']
    for (const pref of preferred) {
      if (nestedProps.some((p: any) => p?.name === pref)) return pref
    }

    return nestedProps[0]?.name ?? null
  }

  getArrayObjectLabel(obj: any, fieldName?: string): string {
    if (!obj || typeof obj !== 'object') return this.safeToString(obj)
    if (this.isKeyValueObject(obj)) return this.safeToString(obj.key)

    const primary = fieldName ? this.getArrayPrimaryNestedProp(fieldName) : null
    if (primary && obj?.[primary] !== undefined) return this.safeToString(obj[primary])

    const byName = obj?.name || obj?.title || obj?.type || obj?.id
    if (byName !== undefined) return this.safeToString(byName)

    const keys = Object.keys(obj)
    if (keys.length > 0) return this.safeToString(obj[keys[0]])

    return 'Item'
  }

  private isKeyValueObject(value: unknown): value is { key: unknown; value: unknown } {
    return !!value && typeof value === 'object' && 'key' in (value as any) && 'value' in (value as any)
  }

  safeToString(value: unknown): string {
    if (value === null || value === undefined) return ''
    if (typeof value === 'string') return value
    if (typeof value === 'number' || typeof value === 'boolean') return String(value)
    try {
      return JSON.stringify(value)
    } catch {
      return String(value)
    }
  }
}
