import { StructureUtil } from '@/util/StructureUtil'

export class PropertyInspector {
  private structureProperties: any = {}
  private items: any[] = []
  private _cache = new Map<string, any[]>()

  setStructureProperties(properties: any): void {
    this.structureProperties = properties
    this._cache.clear()
  }

  setItems(items: any[]): void {
    this.items = items
    this._cache.clear()
  }

  isDateField(field: string): boolean {
    return StructureUtil.getPropertyDefinition(field, this.structureProperties)?.type?.type === 'date'
  }


  isPrimitiveArrayAtPath(...path: string[]): boolean {
    if (path.length === 0) return false

    if (path.length === 1) {
      const prop = this.structureProperties.find((p: any) => p.name === path[0])
      if (!prop || prop.type?.type !== 'array') return false
      const containsType = prop.type.contains?.type
      return ['string', 'number', 'boolean', 'date'].includes(containsType)
    }

    // For deeper levels: get properties at the parent path and check the last segment
    const parentPath = path.slice(0, -1)
    const targetName = path[path.length - 1]
    const parentProps = this.getPropertiesAtPath(...parentPath)
    const targetProp = parentProps.find((p: any) => p.name === targetName)
    if (!targetProp || targetProp.type?.type !== 'array') return false
    const containsType = targetProp.type.contains?.type
    return ['string', 'number', 'boolean', 'date'].includes(containsType)
  }

  isPrimitiveArray(fieldName: string): boolean {
    return this.isPrimitiveArrayAtPath(fieldName)
  }

  isNestedPrimitiveArray(fieldName: string, nestedPropName: string): boolean {
    return this.isPrimitiveArrayAtPath(fieldName, nestedPropName)
  }

  isDeepNestedPrimitiveArray(fieldName: string, nestedProp: string, deepProp: string): boolean {
    return this.isPrimitiveArrayAtPath(fieldName, nestedProp, deepProp)
  }

  getPropertiesAtPath(...path: string[]): any[] {
    if (path.length === 0) return []

    const cacheKey = `path:${path.join('.')}`
    if (this._cache.has(cacheKey)) {
      return this._cache.get(cacheKey)!
    }

    const rootProp = this.structureProperties.find((p: any) => p.name === path[0])
    if (!rootProp) {
      this._cache.set(cacheKey, [])
      return []
    }

    let properties = this.extractChildProperties(rootProp)

    if (path.length === 1 && rootProp.type?.type === 'array' && rootProp.type.contains?.type === 'object') {
      const allProps = new Map<string, any>()
      properties.forEach((p: any) => allProps.set(p.name, p))
      for (const item of this.items) {
        const arr = item[path[0]]
        if (Array.isArray(arr)) {
          for (const obj of arr) {
            if (obj && typeof obj === 'object') {
              Object.keys(obj).forEach((key) => {
                if (!allProps.has(key)) {
                  const value = obj[key]
                  const inferredType = Array.isArray(value) ? 'array' : typeof value
                  allProps.set(key, { name: key, type: { type: inferredType } })
                }
              })
            }
          }
        }
      }
      properties = Array.from(allProps.values())
    }

    for (let i = 1; i < path.length; i++) {
      const nextProp = properties.find((p: any) => p.name === path[i])
      if (!nextProp) {
        this._cache.set(cacheKey, [])
        return []
      }
      properties = this.extractChildProperties(nextProp)
    }

    const result = this.sortProperties(this.enrichProperties(properties))
    this._cache.set(cacheKey, result)
    return result
  }

  getNestedProperties(fieldName: string): any[] {
    return this.getPropertiesAtPath(fieldName)
  }

  getNestedObjectProperties(fieldName: string, nestedProp: string): any[] {
    return this.getPropertiesAtPath(fieldName, nestedProp)
  }

  getDeepNestedProperties(fieldName: string, nestedProp: string, deepProp: string): any[] {
    return this.getPropertiesAtPath(fieldName, nestedProp, deepProp)
  }

  getVeryDeepNestedProperties(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string): any[] {
    return this.getPropertiesAtPath(fieldName, nestedProp, deepProp, veryDeepProp)
  }

  private extractChildProperties(prop: any): any[] {
    const type = prop.type?.type

    if (type === 'object') {
      return prop.type?.properties || []
    } else if (type === 'array') {
      const containsType = prop.type.contains?.type
      if (containsType === 'object') {
        return prop.type.contains?.properties || []
      } else if (containsType === 'union') {
        const unionTypes = prop.type.contains?.types || []
        const allProperties = new Map<string, any>()
        unionTypes.forEach((unionType: any) => {
          if (unionType.type === 'object' && unionType.properties) {
            unionType.properties.forEach((p: any) => {
              if (!allProperties.has(p.name)) {
                allProperties.set(p.name, p)
              }
            })
          }
        })
        return Array.from(allProperties.values())
      }
    }
    return []
  }

  
  private enrichProperties(properties: any[]): any[] {
    return properties.map((p: any) => {
      const isArray = p.type?.type === 'array'
      const containsType = p.type?.contains?.type
      const isExpandableArray = isArray && (containsType === 'union' || (containsType === 'object' && p.type.contains?.properties))

      return {
        ...p,
        name: p.name,
        type: p.type,
        isObject: p.type?.type === 'object',
        isArray: isExpandableArray,
        isUnionArray: isArray && containsType === 'union'
      }
    })
  }


  private sortProperties(properties: any[]): any[] {
    if (properties.length === 0) return properties

    const sorted = [...properties]

    const typeIndex = sorted.findIndex(p => p.name === 'type')
    if (typeIndex > 0) {
      const typeProp = sorted.splice(typeIndex, 1)[0]
      sorted.unshift(typeProp)
    }

    const nameIndex = sorted.findIndex(p => p.name === 'name')
    if (nameIndex > 0) {
      const nameProp = sorted.splice(nameIndex, 1)[0]
      const insertIndex = sorted[0]?.name === 'type' ? 1 : 0
      sorted.splice(insertIndex, 0, nameProp)
    }

    return sorted
  }
}
