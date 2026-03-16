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

  isPrimitiveArray(fieldName: string): boolean {
    const prop = this.structureProperties.find((p: any) => p.name === fieldName)
    if (!prop || prop.type?.type !== 'array') return false
    const containsType = prop.type.contains?.type
    return ['string', 'number', 'boolean', 'date'].includes(containsType)
  }

  isNestedPrimitiveArray(fieldName: string, nestedPropName: string): boolean {
    const nestedProps = this.getNestedProperties(fieldName)
    const nestedProp = nestedProps.find(p => p.name === nestedPropName)
    if (!nestedProp || nestedProp.type?.type !== 'array') return false
    const containsType = nestedProp.type.contains?.type
    return ['string', 'number', 'boolean', 'date'].includes(containsType)
  }

  isDeepNestedPrimitiveArray(fieldName: string, nestedProp: string, deepProp: string): boolean {
    const deepProps = this.getDeepNestedProperties(fieldName, nestedProp, deepProp)
    if (!deepProps || deepProps.length === 0) return false

    const prop = this.structureProperties.find((p: any) => p.name === fieldName)
    if (!prop) return false

    const nestedProperties = this.getNestedObjectProperties(fieldName, nestedProp)
    const deepProperty = nestedProperties.find((p: any) => p.name === deepProp)
    if (!deepProperty || deepProperty.type?.type !== 'array') return false

    const containsType = deepProperty.type.contains?.type
    return ['string', 'number', 'boolean', 'date'].includes(containsType)
  }

  getNestedProperties(fieldName: string): any[] {
    const cacheKey = `nested:${fieldName}`
    if (this._cache.has(cacheKey)) {
      return this._cache.get(cacheKey)!
    }

    const prop = this.structureProperties.find((p: any) => p.name === fieldName)
    if (!prop) {
      const result: any[] = []
      this._cache.set(cacheKey, result)
      return result
    }

    const type = prop.type?.type

    let properties: any[] = []
    if (type === 'object') {
      properties = prop.type?.properties || []
    } else if (type === 'array') {
      const containsType = prop.type.contains?.type
      if (containsType === 'union') {
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
        properties = Array.from(allProperties.values())
      } else if (containsType === 'object') {
        const schemaProps = prop.type.contains?.properties || []
        const allProps = new Map<string, any>()

        schemaProps.forEach((p: any) => {
          allProps.set(p.name, p)
        })

        for (const item of this.items) {
          const arr = item[fieldName]
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
    }

    if (properties.length > 0) {
      const sortedProps = [...properties]

      const typeIndex = sortedProps.findIndex(p => p.name === 'type')
      if (typeIndex > 0) {
        const typeProp = sortedProps.splice(typeIndex, 1)[0]
        sortedProps.unshift(typeProp)
      }

      const nameIndex = sortedProps.findIndex(p => p.name === 'name')
      if (nameIndex > 0) {
        const nameProp = sortedProps.splice(nameIndex, 1)[0]
        const insertIndex = sortedProps[0]?.name === 'type' ? 1 : 0
        sortedProps.splice(insertIndex, 0, nameProp)
      }

      const result = sortedProps.map(p => {
        const isArray = p.type?.type === 'array'
        const containsType = p.type?.contains?.type
        const isExpandableArray = isArray && (containsType === 'union' || (containsType === 'object' && p.type.contains?.properties))

        return {
          ...p,
          isObject: p.type?.type === 'object',
          isArray: isExpandableArray,
          isUnionArray: isArray && containsType === 'union'
        }
      })
      this._cache.set(cacheKey, result)
      return result
    }

    const result: any[] = []
    this._cache.set(cacheKey, result)
    return result
  }

  getNestedObjectProperties(fieldName: string, nestedProp: string): any[] {
    const cacheKey = `nestedObj:${fieldName}.${nestedProp}`
    if (this._cache.has(cacheKey)) {
      return this._cache.get(cacheKey)!
    }

    const prop = this.structureProperties.find((p: any) => p.name === fieldName)
    if (!prop) {
      const result: any[] = []
      this._cache.set(cacheKey, result)
      return result
    }

    const type = prop.type?.type

    let parentProperties: any[] = []
    if (type === 'object') {
      parentProperties = prop.type?.properties || []
    } else if (type === 'array') {
      const containsType = prop.type.contains?.type
      if (containsType === 'object') {
        parentProperties = prop.type.contains?.properties || []
      }
    }

    const nestedProperty = parentProperties.find((p: any) => p.name === nestedProp)
    if (!nestedProperty) {
      const result: any[] = []
      this._cache.set(cacheKey, result)
      return result
    }

    let properties: any[] = []
    const nestedType = nestedProperty.type?.type

    if (nestedType === 'object') {
      properties = nestedProperty.type?.properties || []
    } else if (nestedType === 'array') {
      const containsType = nestedProperty.type.contains?.type
      if (containsType === 'object') {
        properties = nestedProperty.type.contains?.properties || []
      }
    }

    const result = properties.map((p: any) => {
      const isArray = p.type?.type === 'array'
      const containsType = p.type?.contains?.type

      return {
        name: p.name,
        type: p.type,
        isArray: isArray,
        isUnionArray: isArray && containsType === 'union',
        ...p
      }
    })
    this._cache.set(cacheKey, result)
    return result
  }

  getDeepNestedProperties(fieldName: string, nestedProp: string, deepProp: string): any[] {
    const cacheKey = `deep:${fieldName}.${nestedProp}.${deepProp}`
    if (this._cache.has(cacheKey)) {
      return this._cache.get(cacheKey)!
    }

    const prop = this.structureProperties.find((p: any) => p.name === fieldName)
    if (!prop) {
      const result: any[] = []
      this._cache.set(cacheKey, result)
      return result
    }

    const type = prop.type?.type
    let parentProperties: any[] = []
    if (type === 'object') {
      parentProperties = prop.type?.properties || []
    } else if (type === 'array') {
      const containsType = prop.type.contains?.type
      if (containsType === 'object') {
        parentProperties = prop.type.contains?.properties || []
      }
    }

    const nestedProperty = parentProperties.find((p: any) => p.name === nestedProp)
    if (!nestedProperty) {
      const result: any[] = []
      this._cache.set(cacheKey, result)
      return result
    }

    let nestedProperties: any[] = []
    const nestedType = nestedProperty.type?.type

    if (nestedType === 'object') {
      nestedProperties = nestedProperty.type?.properties || []
    } else if (nestedType === 'array') {
      const containsType = nestedProperty.type.contains?.type
      if (containsType === 'object') {
        nestedProperties = nestedProperty.type.contains?.properties || []
      }
    }

    const deepProperty = nestedProperties.find((p: any) => p.name === deepProp)
    if (!deepProperty) {
      const result: any[] = []
      this._cache.set(cacheKey, result)
      return result
    }

    let properties: any[] = []
    const deepType = deepProperty.type?.type

    if (deepType === 'object') {
      properties = deepProperty.type?.properties || []
    } else if (deepType === 'array') {
      const containsType = deepProperty.type.contains?.type
      if (containsType === 'object') {
        properties = deepProperty.type.contains?.properties || []
      } else if (containsType === 'union') {
        const unionTypes = deepProperty.type.contains?.types || []
        const allProperties = new Map<string, any>()

        unionTypes.forEach((unionType: any) => {
          if (unionType.type === 'object' && unionType.properties) {
            unionType.properties.forEach((prop: any) => {
              if (!allProperties.has(prop.name)) {
                allProperties.set(prop.name, prop)
              }
            })
          }
        })

        properties = Array.from(allProperties.values())
      }
    }

    const result = properties.map((p: any) => ({
      name: p.name,
      type: p.type,
      isArray: p.type?.type === 'array',
      ...p
    }))
    this._cache.set(cacheKey, result)
    return result
  }

  getVeryDeepNestedProperties(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string): any[] {
    const cacheKey = `veryDeep:${fieldName}.${nestedProp}.${deepProp}.${veryDeepProp}`
    if (this._cache.has(cacheKey)) {
      return this._cache.get(cacheKey)!
    }

    const deepProps = this.getDeepNestedProperties(fieldName, nestedProp, deepProp)
    const veryDeepProperty = deepProps.find((p: any) => p.name === veryDeepProp)
    if (!veryDeepProperty) {
      const result: any[] = []
      this._cache.set(cacheKey, result)
      return result
    }

    let properties: any[] = []
    const veryDeepType = veryDeepProperty.type?.type

    if (veryDeepType === 'array') {
      const containsType = veryDeepProperty.type.contains?.type
      if (containsType === 'union') {
        const unionTypes = veryDeepProperty.type.contains?.types || []
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
        properties = Array.from(allProperties.values())
      } else if (containsType === 'object') {
        properties = veryDeepProperty.type.contains?.properties || []
      }
    } else if (veryDeepType === 'object') {
      properties = veryDeepProperty.type?.properties || []
    }

    if (properties.length > 0) {
      const sortedProps = [...properties]
      const typeIndex = sortedProps.findIndex((p: any) => p.name === 'type')
      if (typeIndex > 0) {
        const typeProp = sortedProps.splice(typeIndex, 1)[0]
        sortedProps.unshift(typeProp)
      }
      const result = sortedProps.map((p: any) => ({ name: p.name, ...p }))
      this._cache.set(cacheKey, result)
      return result
    }

    const result = properties.map((p: any) => ({ name: p.name, ...p }))
    this._cache.set(cacheKey, result)
    return result
  }
}
