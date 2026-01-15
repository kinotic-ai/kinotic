
<script lang="ts">
import { Component, Vue, Prop } from 'vue-facing-decorator'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Toolbar from 'primevue/toolbar'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'

import { Pageable, type Page, Order, Direction, type Identifiable } from '@mindignited/continuum-client'
import { Structure, type IStructureService, Structures, type IEntitiesService } from '@mindignited/structures-api'

type EntityItem = Identifiable<string> & { id: string } & Record<string, any>

interface HeaderDef {
  header: string
  field: string
  sortable: boolean
  width: number
  isCollapsable?: boolean
  expandedWidth?: number | null
  [key: string]: any
}

import DatetimeUtil from '@/util/DatetimeUtil'
import { StructureUtil } from '@/util/StructureUtil'
import { rowColors } from '@/util/rowColors'

@Component({
  components: {
    DataTable,
    Column,
    Toolbar,
    Button,
    InputText
  }
})
class EntityList extends Vue {
  @Prop({ type: String }) structureId?: string

  loading = false
  finishedInitialLoad = false
  items: EntityItem[] = []
  totalItems = 0
  searchText: string | null = null

  keys: string[] = []
  headers: HeaderDef[] = []
  structureProperties: any = {}
  structure!: Structure
  
  // Expansion state tracking
  expandedColumns: Set<string> = new Set() // Track which columns are expanded
  expandedRows: Map<string, Set<string>> = new Map() // Track which rows have expanded cells (rowId -> Set of field names)
  expandedNestedObjects: Set<string> = new Set() // Track which nested objects are expanded (e.g., 'payment.method')
  expandedDeepNested: Set<string> = new Set() // Track 4th level expansion (e.g., 'qualifications.education.research')
  expandedNestedArrays: Map<string, Map<string, Set<string>>> = new Map() // Track nested array expansions: Map<rowId, Map<parentPath, Set<arrayFieldName>>>
  expandedVeryDeepNested: Set<string> = new Set() // Track 5th level expansion (e.g., 'qualifications.education.research.funding')
  
  // Column resize state
  resizingColumn: any = null
  startX = 0
  startWidth = 0
  wasExpanded = false
  nestedColumnWidths: Map<string, number> = new Map() // Store nested column widths persistently
  deepNestedColumnWidths: Map<string, number> = new Map() // Store 3rd level column widths
  veryDeepNestedColumnWidths: Map<string, number> = new Map() // Store 4th level column widths
  
  entitiesService: IEntitiesService = Structures.getEntitiesService()
  structureService: IStructureService = Structures.getStructureService()

  options = {
    rows: 50,
    first: 0,
    sortField: '',
    sortOrder: 1
  }

  mounted() {
    this.find()
const paramId = this.$route.params.id
const id = this.structureId || (Array.isArray(paramId) ? paramId[0] : paramId)

if (!id) {
      this.displayAlert("Missing structure ID.")
      return
    }

    this.structureService.findById(id)
      .then((structure) => {
        this.structure = structure
        this.structureProperties = structure.entityDefinition.properties
        for (const property of this.structureProperties) {
          if (property) {
            const fieldName = property.name[0].toUpperCase() + property.name.slice(1)
            let sortable = true
            if (
              ['ref', 'array', 'object'].includes(property.type.type) ||
              (property.type.type === 'string' && StructureUtil.hasDecorator('Text', property.decorators))
            ) {
              sortable = false
            }
            const isComplex = ['ref', 'array', 'object'].includes(property.type.type)
            const headerDef: any = {
              header: fieldName,
              field: property.name,
              sortable: sortable,
              width: property.name === 'id' ? 220 : (isComplex ? 240 : (sortable ? 120 : 160)),
              isCollapsable: isComplex || property?.name === 'addresses' || property?.name === 'pet',
              expandedWidth: isComplex ? 600 : null // Width when expanded
            }
            this.headers.push(headerDef)
            this.keys.push(property.name)
          }
        }

        this.find()
      })
      .catch((error) => {
        console.error(`Error during structure retrieval: ${error.message}`)
        this.displayAlert(error.message)
      })
  }

  beforeUnmount() {
    document.removeEventListener('mousemove', this.onColumnResize)
    document.removeEventListener('mouseup', this.stopColumnResize)
    document.removeEventListener('mousemove', this.onNestedColumnResize)
    document.removeEventListener('mouseup', this.stopNestedColumnResize)
    document.removeEventListener('mousemove', this.onDeepNestedColumnResize)
    document.removeEventListener('mouseup', this.stopDeepNestedColumnResize)
    document.removeEventListener('mousemove', this.onVeryDeepNestedColumnResize)
    document.removeEventListener('mouseup', this.stopVeryDeepNestedColumnResize)
    document.removeEventListener('mousemove', this.onUltraDeepNestedColumnResize)
    document.removeEventListener('mouseup', this.stopUltraDeepNestedColumnResize)
  }

  formatDate(date: string): string {
    return DatetimeUtil.formatDate(date)
  }

  isDateField(field: string): boolean {
    return StructureUtil.getPropertyDefinition(field, this.structureProperties)?.type?.type === 'date'
  }

  // Toggle column expansion (header click)
  toggleColumnExpansion(fieldName: string) {
    if (this.expandedColumns.has(fieldName)) {
      this.expandedColumns.delete(fieldName)
    } else {
      this.expandedColumns.add(fieldName)
    }
  }

  // Toggle row cell expansion (cell value click) - for arrays, this expands the list
  toggleRowExpansion(rowId: string, fieldName: string) {
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



  // Check if a column is expanded
  isColumnExpanded(fieldName: string): boolean {
    return this.expandedColumns.has(fieldName)
  }

  // Check if a row cell is expanded
  isRowCellExpanded(rowId: string, fieldName: string): boolean {
    return this.expandedRows.get(rowId)?.has(fieldName) || false
  }

  // Get row colors
  get rowColors() {
    return rowColors
  }

  // Check if field is an array of primitives (not objects)
  isPrimitiveArray(fieldName: string): boolean {
    const prop = this.structureProperties.find((p: any) => p.name === fieldName)
    if (!prop || prop.type?.type !== 'array') return false
    
    const containsType = prop.type.contains?.type
    return ['string', 'number', 'boolean', 'date'].includes(containsType)
  }

  // Check if nested property is a primitive array
  isNestedPrimitiveArray(fieldName: string, nestedPropName: string): boolean {
    const nestedProps = this.getNestedProperties(fieldName)
    const nestedProp = nestedProps.find(p => p.name === nestedPropName)
    if (!nestedProp || nestedProp.type?.type !== 'array') return false
    
    const containsType = nestedProp.type.contains?.type
    return ['string', 'number', 'boolean', 'date'].includes(containsType)
  }

  // Check if deep nested property is a primitive array
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

  // Get nested properties for a field
  getNestedProperties(fieldName: string): any[] {
    const prop = this.structureProperties.find((p: any) => p.name === fieldName)
    if (!prop) return []

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
      }
      else if (containsType === 'object') {
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
      
      return sortedProps.map(p => {
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
    }
    
    return []
  }

  // Toggle nested object expansion (3rd level)
  toggleNestedObjectExpansion(fieldName: string, nestedProp: string) {
    const key = `${fieldName}.${nestedProp}`
    if (this.expandedNestedObjects.has(key)) {
      this.expandedNestedObjects.delete(key)
    } else {
      this.expandedNestedObjects.add(key)
    }
  }

  // Check if nested object is expanded
  isNestedObjectExpanded(fieldName: string, nestedProp: string): boolean {
    return this.expandedNestedObjects.has(`${fieldName}.${nestedProp}`)
  }

  // Toggle deeply nested expansion (4th level - e.g., research within education)
  toggleDeepNestedExpansion(fieldName: string, nestedProp: string, deepProp: string) {
    const key = `${fieldName}.${nestedProp}.${deepProp}`
    if (this.expandedDeepNested.has(key)) {
      this.expandedDeepNested.delete(key)
    } else {
      this.expandedDeepNested.add(key)
    }
  }

  // Check if deeply nested item is expanded
  isDeepNestedExpanded(fieldName: string, nestedProp: string, deepProp: string): boolean {
    return this.expandedDeepNested.has(`${fieldName}.${nestedProp}.${deepProp}`)
  }

  // Toggle very deeply nested expansion (5th level - e.g., funding within research)
  toggleVeryDeepNestedExpansion(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string) {
    const key = `${fieldName}.${nestedProp}.${deepProp}.${veryDeepProp}`
    if (this.expandedVeryDeepNested.has(key)) {
      this.expandedVeryDeepNested.delete(key)
    } else {
      this.expandedVeryDeepNested.add(key)
    }
  }

  // Check if very deeply nested item is expanded
  isVeryDeepNestedExpanded(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string): boolean {
    return this.expandedVeryDeepNested.has(`${fieldName}.${nestedProp}.${deepProp}.${veryDeepProp}`)
  }

  // Get properties of a very deeply nested array (5th level - e.g., funding within research)
  getVeryDeepNestedProperties(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string): any[] {
    const deepProps = this.getDeepNestedProperties(fieldName, nestedProp, deepProp)
    const veryDeepProperty = deepProps.find((p: any) => p.name === veryDeepProp)
    if (!veryDeepProperty) return []

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
      }
      else if (containsType === 'object') {
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
      return sortedProps.map((p: any) => ({ name: p.name, ...p }))
    }

    return properties.map((p: any) => ({ name: p.name, ...p }))
  }

  // Toggle nested array expansion (for +n click in nested arrays like research)
  toggleNestedArrayExpansion(rowId: string, parentPath: string, arrayField: string) {
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

  // Check if nested array is expanded for row
  isNestedArrayExpanded(rowId: string, parentPath: string, arrayField: string): boolean {
    return this.expandedNestedArrays.get(rowId)?.get(parentPath)?.has(arrayField) || false
  }

  // Get properties of a deeply nested array (4th level)
  getDeepNestedProperties(fieldName: string, nestedProp: string, deepProp: string): any[] {
    const prop = this.structureProperties.find((p: any) => p.name === fieldName)
    if (!prop) return []

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
    if (!nestedProperty) return []

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
    if (!deepProperty) return []

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

    return properties.map((p: any) => ({
      name: p.name,
      type: p.type,
      isArray: p.type?.type === 'array',
      ...p
    }))
  }

  // Get properties of a nested object (3rd level)
  getNestedObjectProperties(fieldName: string, nestedProp: string): any[] {
    const prop = this.structureProperties.find((p: any) => p.name === fieldName)
    if (!prop) return []

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

    // Find the nested property
    const nestedProperty = parentProperties.find((p: any) => p.name === nestedProp)
    if (!nestedProperty) return []

    let properties: any[] = []
    const nestedType = nestedProperty.type?.type

    if (nestedType === 'object') {
      properties = nestedProperty.type?.properties || []
    }
    else if (nestedType === 'array') {
      const containsType = nestedProperty.type.contains?.type
      if (containsType === 'object') {
        properties = nestedProperty.type.contains?.properties || []
      }
    }

    return properties.map((p: any) => {
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
  }

  // Calculate width for a nested property (accounts for expanded nested objects and arrays)
  getNestedPropWidth(fieldName: string, nestedProp: any): number {
    const key = `${fieldName}.${nestedProp.name}`
    
    if ((nestedProp.isObject || nestedProp.isArray) && this.isNestedObjectExpanded(fieldName, nestedProp.name)) {
      const subProps = this.getNestedObjectProperties(fieldName, nestedProp.name)
      let totalWidth = 0
      for (const subProp of subProps) {
        if (subProp.type?.type === 'array' && this.isDeepNestedExpanded(fieldName, nestedProp.name, subProp.name)) {
          const deepProps = this.getDeepNestedProperties(fieldName, nestedProp.name, subProp.name)
          for (const deepProp of deepProps) {
            // Check if this deepProp is an array and is expanded to 5th level
            if (deepProp.type?.type === 'array' && this.isVeryDeepNestedExpanded(fieldName, nestedProp.name, subProp.name, deepProp.name)) {
              const veryDeepProps = this.getVeryDeepNestedProperties(fieldName, nestedProp.name, subProp.name, deepProp.name)
              for (const vdp of veryDeepProps) {
                totalWidth += Math.max(vdp.name.length * 9 + 32, 100)
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

  // Calculate total dynamic width for expanded parent column
  getExpandedColumnWidth(fieldName: string): number {
    if (!this.isColumnExpanded(fieldName)) return 240
    
    const nestedProps = this.getNestedProperties(fieldName)
    let totalWidth = 0
    
    for (const nestedProp of nestedProps) {
      totalWidth += this.getNestedPropWidth(fieldName, nestedProp)
    }
    
    const hasNestedCustomWidths = nestedProps.some(p => 
      this.nestedColumnWidths.has(`${fieldName}.${p.name}`)
    )
    
    const hasExpandedNestedColumns = nestedProps.some(p => 
      (p.isObject || p.isArray) && this.isNestedObjectExpanded(fieldName, p.name)
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

  // Calculate consistent width for sub-columns of nested objects/arrays (same width for all rows)
  getNestedObjectSubColumnWidth(fieldName: string, nestedProp: string, subPropName: string): number {
    const key = `${fieldName}.${nestedProp}.${subPropName}`
    if (this.deepNestedColumnWidths.has(key)) {
      return this.deepNestedColumnWidths.get(key)!
    }
    
    if (!this.items || this.items.length === 0) return 80
    
    let maxLength = subPropName.length // Start with header length
    
    for (const item of this.items) {
      const nestedValue = item[fieldName]?.[nestedProp]
      let value
      
      if (Array.isArray(nestedValue) && nestedValue.length > 0) {
        value = nestedValue[0]?.[subPropName]
      }
      else {
        value = nestedValue?.[subPropName]
      }
      
      if (value !== null && value !== undefined) {
        const strValue = String(value)
        maxLength = Math.max(maxLength, strValue.length)
      }
    }
    
    return Math.max(Math.min(maxLength * 9 + 40, 300), 100)
  }

  // Get width for deeply nested properties (4th level - orange headers)
  getDeepNestedSubColumnWidth(fieldName: string, nestedProp: string, arrayProp: string, subPropName: string): number {
    const key = `${fieldName}.${nestedProp}.${arrayProp}.${subPropName}`
    if (this.veryDeepNestedColumnWidths.has(key)) {
      return this.veryDeepNestedColumnWidths.get(key)!
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
    
    return Math.max(Math.min(maxLength * 9 + 40, 300), 100)
  }

  // Get all columns including expanded nested ones
  get allColumns(): any[] {
    const columns: any[] = []
    
    for (const header of this.headers) {
      if (this.isColumnExpanded(header.field)) {
        // Add nested properties as separate columns
        const nestedProps = this.getNestedProperties(header.field)
        for (const nestedProp of nestedProps) {
          columns.push({
            header: nestedProp.name,
            field: `${header.field}.${nestedProp.name}`,
            parentField: header.field,
            sortable: false,
            width: 120,
            isNested: true,
            level: 1
          })
        }
      } else {
        columns.push(header)
      }
    }
    
    return columns
  }

  // Get display value for a cell
  getCellDisplayValue(data: any, field: string): string {
    const value = data[field]
    if (value === null || value === undefined) return 'null'
    
    if (Array.isArray(value)) {
      if (value.length === 0) return '[]'
      const firstItem = value[0]
      if (typeof firstItem !== 'object') {
        return value.length > 1 ? `${firstItem} +${value.length - 1}` : String(firstItem)
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

  onPage(event: any) {
    this.options.rows = event.rows
    this.options.first = event.first
    this.find()
  }

  onSort(event: any) {
    this.options.sortField = event.sortField
    this.options.sortOrder = event.sortOrder
    this.find()
  }

  clearSearch() {
    this.searchText = null
    this.options.first = 0
    this.find()
  }
  
  search() {
    this.options.first = 0
    this.find()
  }

  displayAlert(text: string) {
    console.error('[EntityList Alert]:', text)
    window.alert(text)
  }

  startColumnResize(event: MouseEvent, header: HeaderDef) {
    this.resizingColumn = header
    this.startX = event.pageX
    this.wasExpanded = this.isColumnExpanded(header.field)
    this.startWidth = this.wasExpanded
      ? this.getExpandedColumnWidth(header.field) 
      : header.width
    
    document.addEventListener('mousemove', this.onColumnResize)
    document.addEventListener('mouseup', this.stopColumnResize)
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }

  onColumnResize(event: MouseEvent) {
    if (!this.resizingColumn) return
    
    const diff = event.pageX - this.startX
    const newWidth = Math.max(50, this.startWidth + diff)
    
    if (this.wasExpanded) {
      this.resizingColumn.expandedWidth = newWidth
    } else {
      this.resizingColumn.width = newWidth
    }
  }

  stopColumnResize() {
    document.removeEventListener('mousemove', this.onColumnResize)
    document.removeEventListener('mouseup', this.stopColumnResize)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    this.resizingColumn = null
  }

  // Nested column resize methods
  startNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: any) {
    this.resizingColumn = { parentField, nestedProp }
    this.startX = event.pageX
    this.startWidth = this.getNestedPropWidth(parentField, nestedProp)
    
    document.addEventListener('mousemove', this.onNestedColumnResize)
    document.addEventListener('mouseup', this.stopNestedColumnResize)
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }

  onNestedColumnResize(event: MouseEvent) {
    if (!this.resizingColumn || !this.resizingColumn.nestedProp) return
    
    const diff = event.pageX - this.startX
    const newWidth = Math.max(80, this.startWidth + diff)
    
    const key = `${this.resizingColumn.parentField}.${this.resizingColumn.nestedProp.name}`
    this.nestedColumnWidths.set(key, newWidth)
    
    const parentField = this.resizingColumn.parentField
    const header = this.headers.find(h => h.field === parentField)
    if (header) {
      const nestedProps = this.getNestedProperties(parentField)
      let totalWidth = 0
      for (const prop of nestedProps) {
        totalWidth += this.getNestedPropWidth(parentField, prop)
      }
      header.expandedWidth = totalWidth
    }
  }

  stopNestedColumnResize() {
    document.removeEventListener('mousemove', this.onNestedColumnResize)
    document.removeEventListener('mouseup', this.stopNestedColumnResize)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    this.resizingColumn = null
  }

  // Deep nested column resize methods (3rd level)
  startDeepNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: string, deepProp: any) {
    this.resizingColumn = { parentField, nestedProp, deepProp, level: 'deep' }
    this.startX = event.pageX
    this.startWidth = this.getNestedObjectSubColumnWidth(parentField, nestedProp, deepProp.name)
    
    document.addEventListener('mousemove', this.onDeepNestedColumnResize)
    document.addEventListener('mouseup', this.stopDeepNestedColumnResize)
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }

  onDeepNestedColumnResize(event: MouseEvent) {
    if (!this.resizingColumn || this.resizingColumn.level !== 'deep') return
    
    const diff = event.pageX - this.startX
    const newWidth = Math.max(80, this.startWidth + diff)
    
    const key = `${this.resizingColumn.parentField}.${this.resizingColumn.nestedProp}.${this.resizingColumn.deepProp.name}`
    this.deepNestedColumnWidths.set(key, newWidth)
    
    const nestedKey = `${this.resizingColumn.parentField}.${this.resizingColumn.nestedProp}`
    const parentField = this.resizingColumn.parentField
    const nestedPropName = this.resizingColumn.nestedProp
    const deepProps = this.getNestedObjectProperties(parentField, nestedPropName)
    let totalWidth = 0
    for (const prop of deepProps) {
      totalWidth += this.getNestedObjectSubColumnWidth(parentField, nestedPropName, prop.name)
    }
    this.nestedColumnWidths.set(nestedKey, totalWidth)
    
    const header = this.headers.find(h => h.field === parentField)
    if (header) {
      const nestedProps = this.getNestedProperties(parentField)
      let parentTotalWidth = 0
      for (const prop of nestedProps) {
        parentTotalWidth += this.getNestedPropWidth(parentField, prop)
      }
      header.expandedWidth = parentTotalWidth
      header.width = parentTotalWidth
    }
  }

  stopDeepNestedColumnResize() {
    document.removeEventListener('mousemove', this.onDeepNestedColumnResize)
    document.removeEventListener('mouseup', this.stopDeepNestedColumnResize)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    this.resizingColumn = null
  }

  // Very deep nested column resize methods (4th level)
  startVeryDeepNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: string, deepProp: string, veryDeepProp: any) {
    this.resizingColumn = { parentField, nestedProp, deepProp, veryDeepProp, level: 'veryDeep' }
    this.startX = event.pageX
    this.startWidth = this.getDeepNestedSubColumnWidth(parentField, nestedProp, deepProp, veryDeepProp.name)
    
    document.addEventListener('mousemove', this.onVeryDeepNestedColumnResize)
    document.addEventListener('mouseup', this.stopVeryDeepNestedColumnResize)
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }

  onVeryDeepNestedColumnResize(event: MouseEvent) {
    if (!this.resizingColumn || this.resizingColumn.level !== 'veryDeep') return
    
    const diff = event.pageX - this.startX
    const newWidth = Math.max(80, this.startWidth + diff)
    
    const key = `${this.resizingColumn.parentField}.${this.resizingColumn.nestedProp}.${this.resizingColumn.deepProp}.${this.resizingColumn.veryDeepProp.name}`
    this.veryDeepNestedColumnWidths.set(key, newWidth)
    
    // Cascade width updates up the hierarchy
    this.updateParentWidthsAfterDeepResize(
      this.resizingColumn.parentField,
      this.resizingColumn.nestedProp,
      this.resizingColumn.deepProp
    )
  }

  stopVeryDeepNestedColumnResize() {
    document.removeEventListener('mousemove', this.onVeryDeepNestedColumnResize)
    document.removeEventListener('mouseup', this.stopVeryDeepNestedColumnResize)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    this.resizingColumn = null
  }

  // Ultra deep nested column resize methods (5th level)
  startUltraDeepNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: string, deepProp: string, veryDeepProp: string, ultraDeepProp: any) {
    this.resizingColumn = { parentField, nestedProp, deepProp, veryDeepProp, ultraDeepProp, level: 'ultraDeep' }
    this.startX = event.pageX
    this.startWidth = Math.max(ultraDeepProp.name.length * 9 + 32, 100)
    
    document.addEventListener('mousemove', this.onUltraDeepNestedColumnResize)
    document.addEventListener('mouseup', this.stopUltraDeepNestedColumnResize)
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }

  onUltraDeepNestedColumnResize(event: MouseEvent) {
    if (!this.resizingColumn || this.resizingColumn.level !== 'ultraDeep') return
    
    const diff = event.pageX - this.startX
    const newWidth = Math.max(80, this.startWidth + diff)
    
    const key = `${this.resizingColumn.parentField}.${this.resizingColumn.nestedProp}.${this.resizingColumn.deepProp}.${this.resizingColumn.veryDeepProp}.${this.resizingColumn.ultraDeepProp.name}`
    this.veryDeepNestedColumnWidths.set(key, newWidth)
    
    // Cascade width updates up the hierarchy
    this.updateParentWidthsAfterDeepResize(
      this.resizingColumn.parentField,
      this.resizingColumn.nestedProp,
      this.resizingColumn.deepProp
    )
  }

  stopUltraDeepNestedColumnResize() {
    document.removeEventListener('mousemove', this.onUltraDeepNestedColumnResize)
    document.removeEventListener('mouseup', this.stopUltraDeepNestedColumnResize)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    this.resizingColumn = null
  }

  // Helper method to update parent widths after deep nested resize
  updateParentWidthsAfterDeepResize(parentField: string, nestedProp: string, deepProp: string) {
    const deepKey = `${parentField}.${nestedProp}.${deepProp}`
    const veryDeepProps = this.getDeepNestedProperties(parentField, nestedProp, deepProp)
    let deepTotalWidth = 0
    for (const prop of veryDeepProps) {
      deepTotalWidth += this.getDeepNestedSubColumnWidth(parentField, nestedProp, deepProp, prop.name)
    }
    this.deepNestedColumnWidths.set(deepKey, deepTotalWidth)
    
    const nestedKey = `${parentField}.${nestedProp}`
    const deepProps = this.getNestedObjectProperties(parentField, nestedProp)
    let nestedTotalWidth = 0
    for (const prop of deepProps) {
      nestedTotalWidth += this.getNestedObjectSubColumnWidth(parentField, nestedProp, prop.name)
    }
    this.nestedColumnWidths.set(nestedKey, nestedTotalWidth)
    
    const header = this.headers.find(h => h.field === parentField)
    if (header) {
      const nestedProps = this.getNestedProperties(parentField)
      let parentTotalWidth = 0
      for (const prop of nestedProps) {
        parentTotalWidth += this.getNestedPropWidth(parentField, prop)
      }
      header.expandedWidth = parentTotalWidth
    }
  }

  find() {
    if (this.loading) return

    this.loading = true

    const page = this.options.first / this.options.rows
    const orders: Order[] = []
    
    if (this.options.sortField) {
      orders.push(new Order(this.options.sortField, this.options.sortOrder === 1 ? Direction.ASC : Direction.DESC))
    }

    const pageable = Pageable.create(page, this.options.rows, { orders })
    const paramId = this.$route.params.id
    const id = this.structureId || (Array.isArray(paramId) ? paramId[0] : paramId)
    
    const queryPromise = (this.searchText?.length)
    ? this.entitiesService.search(id, this.searchText, pageable)
    : this.entitiesService.findAll(id, pageable)

    queryPromise
    .then((page: Page<any>) => {
        this.items = page.content ?? []
        this.totalItems = page.totalElements ?? 0
        this.loading = false
        
        if (!this.finishedInitialLoad) {
          setTimeout(() => { this.finishedInitialLoad = true }, 500)
        }
      })
      .catch((error: any) => {
        this.displayAlert(error.message)
        this.loading = false
        if (!this.finishedInitialLoad) {
          setTimeout(() => { this.finishedInitialLoad = true }, 500)
        }
      })
  }
}

export default EntityList
</script>

<template>
  <div class="w-full h-full flex flex-col">
    <Toolbar class="!w-full flex-shrink-0">
      <template #start>
        <InputText
          v-model="searchText" 
          placeholder="Search" 
          @keyup.enter="search" 
          @focus="($event.target as HTMLInputElement)?.select()"
          class="w-1/2"
        />
        <Button icon="pi pi-times" class="ml-2" v-if="searchText" @click="clearSearch" />
      </template>
    </Toolbar>

    <div class="flex-1 overflow-auto relative" style="min-height: 0;">
      <table class="w-full border-collapse table-fixed" style="box-sizing: border-box;">
        <thead class="sticky top-0 z-10">
          <tr style="background-color: #101010;">
            <th
              v-for="header in headers"
              :key="header.field"
              :style="{ 
                width: (isColumnExpanded(header.field) ? getExpandedColumnWidth(header.field) : header.width) + 'px',
                maxWidth: (isColumnExpanded(header.field) ? getExpandedColumnWidth(header.field) : header.width) + 'px',
                boxSizing: 'border-box',
                position: 'relative',
                height: '38px'
              }"
              class="px-0 py-0 text-left text-xs font-medium text-white"
              style="border: 1px solid #28282B;"
            >
              <div class="flex items-center gap-1 h-full px-2">
                <span 
                  v-if="header.isCollapsable && !isPrimitiveArray(header.field)" 
                  class="flex-shrink-0 cursor-pointer"
                  @click.stop="toggleColumnExpansion(header.field)"
                >
                  <svg v-if="isColumnExpanded(header.field)" width="7" height="4" viewBox="0 0 7 4" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M1 0.5L3.5 3L6 0.5" stroke="#9B87F5" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                  <svg v-else width="4" height="7" viewBox="0 0 4 7" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M0.5 1L3 3.5L0.5 6" stroke="#9B87F5" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </span>
                <span 
                  class="truncate-text flex-1" 
                  :class="{ 'cursor-pointer': header.isCollapsable && !isPrimitiveArray(header.field) }"
                  @click.stop="(header.isCollapsable && !isPrimitiveArray(header.field)) ? toggleColumnExpansion(header.field) : null"
                >{{ header.header }}</span>
                <div
                  class="resize-handle"
                  @mousedown.stop.prevent="startColumnResize($event, header)"
                  title="Drag to resize"
                ></div>
              </div>
            </th>
          </tr>

          <tr v-if="headers.some(h => isColumnExpanded(h.field) && !isPrimitiveArray(h.field))" :style="{ backgroundColor: rowColors[0].header }">
            <th
              v-for="header in headers"
              :key="'sub-' + header.field"
              :style="{ 
                width: (isColumnExpanded(header.field) ? getExpandedColumnWidth(header.field) : header.width) + 'px',
                maxWidth: (isColumnExpanded(header.field) ? getExpandedColumnWidth(header.field) : header.width) + 'px'
              , boxSizing: 'border-box', flexShrink: '0', height: '29px' }"
              class="px-0 py-0 text-left text-xs font-medium"
              style="border: 1px solid white;"
            >
              <div v-if="isColumnExpanded(header.field)" class="flex w-full h-full">
                <div
                  v-for="nestedProp in getNestedProperties(header.field)"
                  :key="`${header.field}.${nestedProp.name}`"
                  class="px-2 py-1 border-r last:border-r-0 border-gray-300 relative"
                  :style="{ 
                    width: getNestedPropWidth(header.field, nestedProp) + 'px',
                    minWidth: '80px',
                    boxSizing: 'border-box',
                    flexShrink: '0',
                    height: '29px'
                  }"
                >
                  <div class="flex items-center gap-1">
                    <span 
                      v-if="(nestedProp.isObject || nestedProp.isArray) && !isNestedPrimitiveArray(header.field, nestedProp.name)" 
                      class="flex-shrink-0 cursor-pointer"
                      @click.stop="toggleNestedObjectExpansion(header.field, nestedProp.name)"
                    >
                      <svg v-if="isNestedObjectExpanded(header.field, nestedProp.name)" width="7" height="4" viewBox="0 0 7 4" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M1 0.5L3.5 3L6 0.5" stroke="#AFB0B8" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                      <svg v-else width="4" height="7" viewBox="0 0 4 7" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M0.5 1L3 3.5L0.5 6" stroke="#AFB0B8" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                    </span>
                    <span 
                      class="truncate-text text-xs flex-1"
                      :class="{ 'cursor-pointer': (nestedProp.isObject || nestedProp.isArray) && !isNestedPrimitiveArray(header.field, nestedProp.name) }"
                      @click.stop="((nestedProp.isObject || nestedProp.isArray) && !isNestedPrimitiveArray(header.field, nestedProp.name)) ? toggleNestedObjectExpansion(header.field, nestedProp.name) : null"
                    >{{ nestedProp.name }}</span>
                    <div
                      class="resize-handle"
                      @mousedown.stop.prevent="startNestedColumnResize($event, header.field, nestedProp)"
                      title="Drag to resize"
                    ></div>
                  </div>
                </div>
              </div>
            </th>
          </tr>

          <tr v-if="headers.some(h => isColumnExpanded(h.field) && !isPrimitiveArray(h.field) && getNestedProperties(h.field).some(p => isNestedObjectExpanded(h.field, p.name)))" style="background-color: #FAE8FF;">
            <th
              v-for="header in headers"
              :key="'subsub-' + header.field"
              :style="{ 
                width: (isColumnExpanded(header.field) ? getExpandedColumnWidth(header.field) : header.width) + 'px',
                maxWidth: (isColumnExpanded(header.field) ? getExpandedColumnWidth(header.field) : header.width) + 'px'
              , boxSizing: 'border-box', flexShrink: '0', height: '29px' }"
              class="px-0 py-0 text-left text-xs font-medium"
              style="border: 1px solid white;"
            >
              <div v-if="isColumnExpanded(header.field)" class="flex w-full h-full">
                <div
                  v-for="nestedProp in getNestedProperties(header.field)"
                  :key="`${header.field}.${nestedProp.name}`"
                  class="border-r last:border-r-0 border-gray-300"
                  :style="{ 
                    width: getNestedPropWidth(header.field, nestedProp) + 'px',
                    minWidth: '80px',
                    boxSizing: 'border-box',
                    flexShrink: '0'
                  }"
                >
                  <div v-if="isNestedObjectExpanded(header.field, nestedProp.name)" class="flex w-full h-full">
                    <div
                      v-for="deepProp in getNestedObjectProperties(header.field, nestedProp.name)"
                      :key="`${header.field}.${nestedProp.name}.${deepProp.name}`"
                      class="px-2 py-1 border-r last:border-r-0 border-gray-300 relative"
                      :style="{ 
                        width: (() => {
                          // If this deep property is expanded, calculate total width of sub-columns
                          if (isDeepNestedExpanded(header.field, nestedProp.name, deepProp.name)) {
                            const subProps = getDeepNestedProperties(header.field, nestedProp.name, deepProp.name)
                            return subProps.reduce((total, sp) => {
                              // If subProp is an array and expanded to 5th level, calculate 5th-level width
                              if (sp.type?.type === 'array' && isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, sp.name)) {
                                const veryDeepProps = getVeryDeepNestedProperties(header.field, nestedProp.name, deepProp.name, sp.name)
                                return total + veryDeepProps.reduce((sum, vdp) => sum + Math.max(vdp.name.length * 9 + 32, 100), 0)
                              }
                              return total + getDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, sp.name)
                            }, 0)
                          }
                          return getNestedObjectSubColumnWidth(header.field, nestedProp.name, deepProp.name)
                        })() + 'px', 
                        minWidth: '100px',
                        height: '29px',
                        flexShrink: '0'
                      }"
                    >
                      <div class="flex items-center gap-1 h-full">
                        <span 
                          v-if="(deepProp.type?.type === 'array' || deepProp.type?.type === 'object') && !isDeepNestedPrimitiveArray(header.field, nestedProp.name, deepProp.name)" 
                          class="flex-shrink-0 cursor-pointer"
                          @click.stop="toggleDeepNestedExpansion(header.field, nestedProp.name, deepProp.name)"
                        >
                          <svg v-if="isDeepNestedExpanded(header.field, nestedProp.name, deepProp.name)" width="7" height="4" viewBox="0 0 7 4" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M1 0.5L3.5 3L6 0.5" stroke="#AFB0B8" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                          </svg>
                          <svg v-else width="4" height="7" viewBox="0 0 4 7" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M0.5 1L3 3.5L0.5 6" stroke="#AFB0B8" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                          </svg>
                        </span>
                        <span 
                          class="truncate text-xs text-surface-900 flex-1"
                          :class="{ 'cursor-pointer': (deepProp.type?.type === 'array' || deepProp.type?.type === 'object') && !isDeepNestedPrimitiveArray(header.field, nestedProp.name, deepProp.name) }"
                          @click.stop="((deepProp.type?.type === 'array' || deepProp.type?.type === 'object') && !isDeepNestedPrimitiveArray(header.field, nestedProp.name, deepProp.name)) ? toggleDeepNestedExpansion(header.field, nestedProp.name, deepProp.name) : null"
                        >{{ deepProp.name }}</span>
                        <div
                          class="resize-handle"
                          @mousedown.stop.prevent="startDeepNestedColumnResize($event, header.field, nestedProp.name, deepProp)"
                          title="Drag to resize"
                        ></div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </th>
          </tr>

          <tr v-if="headers.some(h => isColumnExpanded(h.field) && getNestedProperties(h.field).some(np => isNestedObjectExpanded(h.field, np.name) && getNestedObjectProperties(h.field, np.name).some(dp => isDeepNestedExpanded(h.field, np.name, dp.name))))" style="background-color: #EDE9FE;">
            <th
              v-for="header in headers"
              :key="'deep-' + header.field"
              :style="{ 
                width: (isColumnExpanded(header.field) ? getExpandedColumnWidth(header.field) : header.width) + 'px',
                maxWidth: (isColumnExpanded(header.field) ? getExpandedColumnWidth(header.field) : header.width) + 'px'
              , boxSizing: 'border-box', flexShrink: '0', height: '29px' }"
              class="px-0 py-0 text-left text-xs font-medium"
              style="border: 1px solid white;"
            >
              <div v-if="isColumnExpanded(header.field)" class="flex w-full h-full">
                <div
                  v-for="nestedProp in getNestedProperties(header.field)"
                  :key="`${header.field}.${nestedProp.name}`"
                  class="border-r last:border-r-0 border-gray-300"
                  :style="{ 
                    width: getNestedPropWidth(header.field, nestedProp) + 'px',
                    minWidth: '80px',
                    boxSizing: 'border-box',
                    flexShrink: '0'
                  }"
                >
                  <div v-if="isNestedObjectExpanded(header.field, nestedProp.name)" class="flex w-full h-full">
                    <div
                      v-for="deepProp in getNestedObjectProperties(header.field, nestedProp.name)"
                      :key="`${header.field}.${nestedProp.name}.${deepProp.name}`"
                      class="border-r last:border-r-0 border-gray-300"
                      :style="{ 
                        width: (() => {
                          if (isDeepNestedExpanded(header.field, nestedProp.name, deepProp.name)) {
                            const subProps = getDeepNestedProperties(header.field, nestedProp.name, deepProp.name)
                            return subProps.reduce((total, sp) => {
                              if (sp.type?.type === 'array' && isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, sp.name)) {
                                const veryDeepProps = getVeryDeepNestedProperties(header.field, nestedProp.name, deepProp.name, sp.name)
                                return total + veryDeepProps.reduce((sum, vdp) => sum + Math.max(vdp.name.length * 9 + 32, 100), 0)
                              }
                              return total + getDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, sp.name)
                            }, 0)
                          }
                          return getNestedObjectSubColumnWidth(header.field, nestedProp.name, deepProp.name)
                        })() + 'px', 
                        minWidth: '80px',
                        boxSizing: 'border-box',
                        flexShrink: '0'
                      }"
                    >
                      <div v-if="isDeepNestedExpanded(header.field, nestedProp.name, deepProp.name)" class="flex w-full h-full">
                        <div
                          v-for="subProp in getDeepNestedProperties(header.field, nestedProp.name, deepProp.name)"
                          :key="`${header.field}.${nestedProp.name}.${deepProp.name}.${subProp.name}`"
                          class="px-2 py-1 border-r last:border-r-0 border-gray-300 relative"
                          :style="{ 
                            width: (() => {
                              if (subProp.type?.type === 'array' && isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, subProp.name)) {
                                const veryDeepProps = getVeryDeepNestedProperties(header.field, nestedProp.name, deepProp.name, subProp.name)
                                return veryDeepProps.reduce((sum, vdp) => sum + Math.max(vdp.name.length * 9 + 32, 100), 0)
                              }
                              return getDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, subProp.name)
                            })() + 'px', 
                            minWidth: '100px',
                            flexShrink: '0',
                            boxSizing: 'border-box'
                          }"
                        >
                          <div class="flex items-center gap-1 h-full">
                            <span 
                              v-if="subProp.type?.type === 'array' && subProp.type?.contains?.type !== 'string' && subProp.type?.contains?.type !== 'number' && subProp.type?.contains?.type !== 'boolean' && subProp.type?.contains?.type !== 'date'" 
                              class="flex-shrink-0 cursor-pointer"
                              @click.stop="toggleVeryDeepNestedExpansion(header.field, nestedProp.name, deepProp.name, subProp.name)"
                            >
                              <svg v-if="isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, subProp.name)" width="7" height="4" viewBox="0 0 7 4" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <path d="M1 0.5L3.5 3L6 0.5" stroke="#AFB0B8" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                              </svg>
                              <svg v-else width="4" height="7" viewBox="0 0 4 7" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <path d="M0.5 1L3 3.5L0.5 6" stroke="#AFB0B8" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                              </svg>
                            </span>
                            <span 
                              class="truncate text-xs text-surface-900 flex-1"
                              :class="{ 'cursor-pointer': subProp.type?.type === 'array' && subProp.type?.contains?.type !== 'string' && subProp.type?.contains?.type !== 'number' && subProp.type?.contains?.type !== 'boolean' && subProp.type?.contains?.type !== 'date' }"
                              @click.stop="(subProp.type?.type === 'array' && subProp.type?.contains?.type !== 'string' && subProp.type?.contains?.type !== 'number' && subProp.type?.contains?.type !== 'boolean' && subProp.type?.contains?.type !== 'date') ? toggleVeryDeepNestedExpansion(header.field, nestedProp.name, deepProp.name, subProp.name) : null"
                            >{{ subProp.name }}</span>
                            <div
                              class="resize-handle"
                              @mousedown.stop.prevent="startVeryDeepNestedColumnResize($event, header.field, nestedProp.name, deepProp.name, subProp)"
                              title="Drag to resize"
                            ></div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </th>
          </tr>

          <tr v-if="headers.some(h => isColumnExpanded(h.field) && getNestedProperties(h.field).some(np => isNestedObjectExpanded(h.field, np.name) && getNestedObjectProperties(h.field, np.name).some(dp => getDeepNestedProperties(h.field, np.name, dp.name).some(vdp => isVeryDeepNestedExpanded(h.field, np.name, dp.name, vdp.name)))))" style="background-color: #7dd3fc;">
            <th
              v-for="header in headers"
              :key="'very-deep-' + header.field"
              :style="{ 
                width: (isColumnExpanded(header.field) ? getExpandedColumnWidth(header.field) : header.width) + 'px',
                maxWidth: (isColumnExpanded(header.field) ? getExpandedColumnWidth(header.field) : header.width) + 'px'
              , boxSizing: 'border-box', flexShrink: '0' }"
              class="px-0 py-0 text-left text-xs font-medium"
              style="border: 1px solid white;"
            >
              <div v-if="isColumnExpanded(header.field)" class="flex w-full">
                <div
                  v-for="nestedProp in getNestedProperties(header.field)"
                  :key="`${header.field}.${nestedProp.name}`"
                  class="border-r last:border-r-0 border-gray-300"
                  :style="{ 
                    width: getNestedPropWidth(header.field, nestedProp) + 'px',
                    minWidth: '80px',
                    boxSizing: 'border-box',
                    flexShrink: '0'
                  }"
                >
                  <div v-if="isNestedObjectExpanded(header.field, nestedProp.name)" class="flex w-full h-full">
                    <div
                      v-for="deepProp in getNestedObjectProperties(header.field, nestedProp.name)"
                      :key="`${header.field}.${nestedProp.name}.${deepProp.name}`"
                      class="border-r last:border-r-0 border-gray-300"
                      :style="{ 
                        width: (() => {
                          if (isDeepNestedExpanded(header.field, nestedProp.name, deepProp.name)) {
                            const subProps = getDeepNestedProperties(header.field, nestedProp.name, deepProp.name)
                            return subProps.reduce((total, sp) => {
                              if (sp.type?.type === 'array' && isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, sp.name)) {
                                const veryDeepProps = getVeryDeepNestedProperties(header.field, nestedProp.name, deepProp.name, sp.name)
                                return total + veryDeepProps.reduce((sum, vdp) => sum + Math.max(vdp.name.length * 9 + 32, 100), 0)
                              }
                              return total + getDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, sp.name)
                            }, 0)
                          }
                          return getNestedObjectSubColumnWidth(header.field, nestedProp.name, deepProp.name)
                        })() + 'px', 
                        minWidth: '100px',
                        flexShrink: '0'
                      }"
                    >
                      <div v-if="isDeepNestedExpanded(header.field, nestedProp.name, deepProp.name)" class="flex w-full h-full">
                        <template
                          v-for="subProp in getDeepNestedProperties(header.field, nestedProp.name, deepProp.name)"
                          :key="`${header.field}.${nestedProp.name}.${deepProp.name}.${subProp.name}`"
                        >
                          <template v-if="subProp.type?.type === 'array' && isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, subProp.name)">
                            <div
                              v-for="veryDeepProp in getVeryDeepNestedProperties(header.field, nestedProp.name, deepProp.name, subProp.name)"
                              :key="`${header.field}.${nestedProp.name}.${deepProp.name}.${subProp.name}.${veryDeepProp.name}`"
                              class="border-r last:border-r-0 border-gray-300 flex items-center px-2 py-1 relative"
                              :style="{ 
                                width: Math.max(veryDeepProp.name.length * 9 + 32, 100) + 'px',
                                minWidth: '100px',
                                boxSizing: 'border-box',
                                flexShrink: '0'
                              }"
                            >
                              <span class="truncate text-xs text-gray-800 flex-1">{{ veryDeepProp.name }}</span>
                              <div
                                class="resize-handle"
                                @mousedown.stop.prevent="startUltraDeepNestedColumnResize($event, header.field, nestedProp.name, deepProp.name, subProp.name, veryDeepProp)"
                                title="Drag to resize"
                              ></div>
                            </div>
                          </template>
                          <div
                            v-if="!(subProp.type?.type === 'array' && isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, subProp.name))"
                            class="border-r last:border-r-0 border-gray-300 px-2 py-1"
                            :style="{ 
                              width: getDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, subProp.name) + 'px', 
                              minWidth: '100px',
                              minHeight: '24px',
                              flexShrink: '0',
                              boxSizing: 'border-box'
                            }"
                          >&nbsp;</div>
                        </template>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </th>
          </tr>
        </thead>

        <tbody>
          <template v-for="item in items" :key="item.id">
            <tr class="hover:bg-gray-50" style="height: 1px;">
              <td
                v-for="header in headers"
                :key="header.field"
                :style="{ 
                  width: (isColumnExpanded(header.field) ? getExpandedColumnWidth(header.field) : header.width) + 'px',
                  maxWidth: (isColumnExpanded(header.field) ? getExpandedColumnWidth(header.field) : header.width) + 'px',
                  boxSizing: 'border-box',
                  flexShrink: '0'
                }"
                class="border border-gray-300 px-0 py-0 text-sm"
                style="height: inherit;"
              >
                <div class="h-full flex items-stretch">
                  <div class="flex-1">
                    <template v-if="isColumnExpanded(header.field)">
                  <template v-if="isPrimitiveArray(header.field) && Array.isArray(item[header.field])">
                    <template v-if="isRowCellExpanded(item.id, header.field)">
                      <div>
                        <div class="flex flex-col">
                          <div
                            v-for="(arrItem, arrIdx) in item[header.field]"
                            :key="arrIdx"
                            class="py-1 text-xs cursor-pointer hover:bg-gray-100 border-b last:border-b-0 border-gray-200"
                            :title="String(arrItem)"
                            @click="toggleRowExpansion(item.id, header.field)"
                          >
                            <div class="px-2 flex items-center justify-between">
                              <span class="truncate">{{ arrItem }}</span>
                              <span v-if="arrIdx === 0" class="text-gray-500 ml-2 flex-shrink-0">^</span>
                            </div>
                          </div>
                        </div>
                      </div>
                    </template>
                    <template v-else>
                      <div class="px-3 py-2">
                        <div
                          class="cursor-pointer hover:bg-gray-100 rounded px-1 flex items-center justify-between"
                          :title="String(item[header.field][0])"
                          @click="item[header.field].length > 1 ? toggleRowExpansion(item.id, header.field) : null"
                        >
                          <span class="truncate">{{ item[header.field][0] }}</span>
                          <span v-if="item[header.field].length > 1" class="text-gray-500 ml-2 flex-shrink-0">
                            +{{ item[header.field].length - 1 }}
                          </span>
                        </div>
                      </div>
                    </template>
                  </template>

                  <template v-else-if="Array.isArray(item[header.field])">
                    <template v-if="isRowCellExpanded(item.id, header.field)">
                      <div class="flex w-full">
                        <div
                          v-for="nestedProp in getNestedProperties(header.field)"
                          :key="`${header.field}.${nestedProp.name}`"
                          class="border-r last:border-r-0 border-gray-300 text-xs"
                          :style="{ 
                            width: getNestedPropWidth(header.field, nestedProp) + 'px',
                            minWidth: '80px',
                            boxSizing: 'border-box',
                            flexShrink: '0'
                          }"
                        >
                          <div class="flex flex-col">
                            <div
                              v-for="(arrItem, arrIdx) in item[header.field]"
                              :key="arrIdx"
                              class="px-3 py-2 cursor-pointer hover:bg-gray-100 border-b last:border-b-0 border-gray-200 flex items-center justify-between"
                              :title="String(arrItem[nestedProp.name] || '')"
                              @click="toggleRowExpansion(item.id, header.field)"
                            >
                              <span class="truncate">{{ arrItem[nestedProp.name] ?? '-' }}</span>
                              <span v-if="arrIdx === 0 && nestedProp.name === 'name'" class="text-gray-500 ml-2 flex-shrink-0">^</span>
                            </div>
                          </div>
                        </div>
                      </div>
                    </template>
                    <template v-else>
                      <div class="flex w-full h-[-webkit-fill-available]">
                        <div
                          v-for="nestedProp in getNestedProperties(header.field)"
                          :key="`${header.field}.${nestedProp.name}`"
                          class="px-3 py-2 border-r last:border-r-0 border-gray-300"
                          :style="{ 
                            width: getNestedPropWidth(header.field, nestedProp) + 'px',
                            minWidth: '80px',
                            boxSizing: 'border-box',
                            flexShrink: '0'
                          }"
                        >
                          <div 
                            class="flex items-center justify-between w-full" 
                            :class="{ 'cursor-pointer hover:bg-gray-100 rounded': nestedProp.name === 'name' && item[header.field].length > 1 }"
                            :title="String(item[header.field][0]?.[nestedProp.name] || '')"
                            @click="(nestedProp.name === 'name' && item[header.field].length > 1) ? toggleRowExpansion(item.id, header.field) : null"
                          >
                            <span class="truncate">{{ item[header.field][0]?.[nestedProp.name] ?? '-' }}</span>
                            <span v-if="nestedProp.name === 'name' && item[header.field].length > 1" class="text-gray-500 ml-2 flex-shrink-0">
                              +{{ item[header.field].length - 1 }}
                            </span>
                          </div>
                        </div>
                      </div>
                    </template>
                  </template>

                  <template v-else-if="typeof item[header.field] === 'object' && item[header.field] !== null">
                    <div class="flex w-full h-[-webkit-fill-available]">
                      <div
                        v-for="nestedProp in getNestedProperties(header.field)"
                        :key="`${header.field}.${nestedProp.name}`"
                        class="border-r last:border-r-0 border-gray-300 text-xs"
                        :style="{ 
                          width: getNestedPropWidth(header.field, nestedProp) + 'px',
                          minWidth: '80px',
                          flexShrink: '0',
                          boxSizing: 'border-box'
                        }"
                      >
                        <template v-if="nestedProp.isArray && isNestedObjectExpanded(header.field, nestedProp.name) && Array.isArray(item[header.field]?.[nestedProp.name])">
                          <div class="flex w-full h-full">
                            <div
                              v-for="arrayProp in getNestedObjectProperties(header.field, nestedProp.name)"
                              :key="`${header.field}.${nestedProp.name}.${arrayProp.name}`"
                              class="border-r last:border-r-0 border-gray-300"
                              :style="{ 
                                width: (() => {
                                  if (arrayProp.type?.type === 'array' && isDeepNestedExpanded(header.field, nestedProp.name, arrayProp.name)) {
                                    const subProps = getDeepNestedProperties(header.field, nestedProp.name, arrayProp.name)
                                    return subProps.reduce((total, sp) => {
                                      if (sp.type?.type === 'array' && isVeryDeepNestedExpanded(header.field, nestedProp.name, arrayProp.name, sp.name)) {
                                        const veryDeepProps = getVeryDeepNestedProperties(header.field, nestedProp.name, arrayProp.name, sp.name)
                                        return total + veryDeepProps.reduce((sum, vdp) => sum + Math.max(vdp.name.length * 9 + 32, 100), 0)
                                      }
                                      return total + getDeepNestedSubColumnWidth(header.field, nestedProp.name, arrayProp.name, sp.name)
                                    }, 0)
                                  }
                                  return getNestedObjectSubColumnWidth(header.field, nestedProp.name, arrayProp.name)
                                })() + 'px', 
                                minWidth: '100px',
                                flexShrink: '0'
                              }"
                            >
                              <template v-if="arrayProp.isUnionArray && isDeepNestedExpanded(header.field, nestedProp.name, arrayProp.name)">
                                <div class="flex w-full h-full">
                                    <template
                                    v-for="subProp in getDeepNestedProperties(header.field, nestedProp.name, arrayProp.name)"
                                    :key="`${header.field}.${nestedProp.name}.${arrayProp.name}.${subProp.name}`"
                                  >
                                    <template v-if="subProp.type?.type === 'array' && isVeryDeepNestedExpanded(header.field, nestedProp.name, arrayProp.name, subProp.name)">
                                      <div
                                        v-for="veryDeepProp in getVeryDeepNestedProperties(header.field, nestedProp.name, arrayProp.name, subProp.name)"
                                        :key="`${header.field}.${nestedProp.name}.${arrayProp.name}.${subProp.name}.${veryDeepProp.name}`"
                                        class="border-r last:border-r-0 border-gray-300 cursor-pointer hover:bg-sky-50 px-2 py-1"
                                        :style="{ 
                                          width: Math.max(veryDeepProp.name.length * 9 + 32, 100) + 'px', 
                                          minWidth: '100px',
                                          boxSizing: 'border-box',
                                          flexShrink: '0'
                                        }"
                                        @click.stop="(() => {
                                          const parentArray = item[header.field]?.[nestedProp.name]
                                          if (Array.isArray(parentArray) && parentArray.length > 0) {
                                            const researchArray = parentArray[0]?.[arrayProp.name]
                                            if (Array.isArray(researchArray) && researchArray.length > 0) {
                                              toggleNestedArrayExpansion(item.id, `${header.field}.${nestedProp.name}.${arrayProp.name}.0`, subProp.name)
                                            }
                                          }
                                        })()"
                                      >
                                        <div class="flex flex-col">
                                          <div
                                            v-for="(arrItem, arrIdx) in (() => {
                                              const parentArray = item[header.field]?.[nestedProp.name]
                                              if (!Array.isArray(parentArray) || parentArray.length === 0) return []
                                              const researchArray = parentArray[0]?.[arrayProp.name]
                                              if (!Array.isArray(researchArray) || researchArray.length === 0) return []
                                              const isRowExpanded = isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)
                                              return isRowExpanded ? researchArray : [researchArray[0]]
                                            })()"
                                            :key="arrIdx"
                                            class="border-b last:border-b-0 border-gray-200"
                                          >
                                            <div
                                              v-for="(fundingItem, fundingIdx) in (() => {
                                                const fundingArray = arrItem?.[subProp.name]
                                                if (!Array.isArray(fundingArray) || fundingArray.length === 0) return []
                                                const isFundingExpanded = isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}.${arrayProp.name}.${arrIdx}`, subProp.name)
                                                return isFundingExpanded ? fundingArray : [fundingArray[0]]
                                              })()"
                                              :key="fundingIdx"
                                              class="py-1 border-b last:border-b-0 border-gray-100 flex items-center justify-between px-2"
                                              :title="String(fundingItem?.[veryDeepProp.name] ?? '')"
                                            >
                                              <span class="truncate">{{ fundingItem?.[veryDeepProp.name] ?? '-' }}</span>
                                              <span 
                                                v-if="fundingIdx === 0 && veryDeepProp.name === getVeryDeepNestedProperties(header.field, nestedProp.name, arrayProp.name, subProp.name)[0].name && isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}.${arrayProp.name}.${arrIdx}`, subProp.name)" 
                                                class="text-gray-500 ml-2 flex-shrink-0"
                                              >^</span>
                                              <span 
                                                v-if="fundingIdx === 0 && veryDeepProp.name === getVeryDeepNestedProperties(header.field, nestedProp.name, arrayProp.name, subProp.name)[0].name && !isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}.${arrayProp.name}.${arrIdx}`, subProp.name) && (arrItem?.[subProp.name]?.length ?? 0) > 1" 
                                                class="text-gray-500 ml-2 flex-shrink-0"
                                              >+{{ (arrItem?.[subProp.name]?.length ?? 1) - 1 }}</span>
                                            </div>
                                          </div>
                                        </div>
                                      </div>
                                    </template>
                                    
                                    <div
                                      v-if="!(subProp.type?.type === 'array' && isVeryDeepNestedExpanded(header.field, nestedProp.name, arrayProp.name, subProp.name))"
                                      class="border-r last:border-r-0 border-gray-300"
                                      :style="{ 
                                        width: getDeepNestedSubColumnWidth(header.field, nestedProp.name, arrayProp.name, subProp.name) + 'px', 
                                        minWidth: '100px',
                                        minHeight: '24px',
                                        flexShrink: '0',
                                        overflow: 'hidden',
                                        boxSizing: 'border-box'
                                      }"
                                    >
                                      <div class="flex flex-col">
                                        <div
                                          v-for="(arrItem, arrIdx) in (() => {
                                            const parentArray = item[header.field]?.[nestedProp.name]
                                            if (!Array.isArray(parentArray) || parentArray.length === 0) return []
                                            const researchArray = parentArray[0]?.[arrayProp.name]
                                            if (!Array.isArray(researchArray) || researchArray.length === 0) return []
                                            const isRowExpanded = isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)
                                            return isRowExpanded ? researchArray : [researchArray[0]]
                                          })()"
                                          :key="arrIdx"
                                          class="border-b last:border-b-0 border-gray-200"
                                        >
                                          <template v-if="Array.isArray(arrItem?.[subProp.name])">
                                            <div 
                                              class="cursor-pointer hover:bg-gray-100 rounded"
                                              @click.stop="toggleNestedArrayExpansion(item.id, `${header.field}.${nestedProp.name}.${arrayProp.name}.${arrIdx}`, subProp.name)"
                                            >
                                              <div v-if="isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}.${arrayProp.name}.${arrIdx}`, subProp.name)" class="flex flex-col">
                                                <div
                                                  v-for="(nestedItem, nestedIdx) in arrItem[subProp.name]"
                                                  :key="nestedIdx"
                                                  class="px-3 py-2 border-b last:border-b-0 border-gray-100 flex items-center justify-between"
                                                  :title="String((() => {
                                                    if (typeof nestedItem === 'object' && nestedItem !== null) {
                                                      const firstValue = Object.values(nestedItem)[0]
                                                      return firstValue ?? '-'
                                                    }
                                                    return nestedItem
                                                  })())"
                                                >
                                                  <span class="truncate">{{ 
                                                    (() => {
                                                      if (typeof nestedItem === 'object' && nestedItem !== null) {
                                                        const firstValue = Object.values(nestedItem)[0]
                                                        return firstValue ?? '-'
                                                      }
                                                      return nestedItem
                                                    })()
                                                  }}</span>
                                                  <span v-if="nestedIdx === 0" class="text-gray-500 ml-2 flex-shrink-0">^</span>
                                                </div>
                                              </div>
                                              <div 
                                                v-else 
                                                class="px-3 py-2 flex items-center justify-between"
                                                :title="String((() => {
                                                  const firstItem = arrItem[subProp.name][0]
                                                  if (typeof firstItem === 'object' && firstItem !== null) {
                                                    const firstValue = Object.values(firstItem)[0]
                                                    return firstValue ?? '-'
                                                  }
                                                  return firstItem
                                                })())"
                                              >
                                                <span class="truncate">{{ 
                                                  (() => {
                                                    const firstItem = arrItem[subProp.name][0]
                                                    if (typeof firstItem === 'object' && firstItem !== null) {
                                                      const firstValue = Object.values(firstItem)[0]
                                                      return firstValue ?? '-'
                                                    }
                                                    return firstItem
                                                  })()
                                                }}</span>
                                                <span v-if="arrItem[subProp.name].length > 1" class="text-gray-500 ml-2 flex-shrink-0">
                                                  +{{ arrItem[subProp.name].length - 1 }}
                                                </span>
                                                <span v-if="arrIdx === 0 && subProp.name === getDeepNestedProperties(header.field, nestedProp.name, arrayProp.name)[0].name" class="text-gray-500 ml-2 flex-shrink-0">
                                                  {{ 
                                                    (() => {
                                                      const parentArray = item[header.field]?.[nestedProp.name]
                                                      if (!Array.isArray(parentArray) || parentArray.length === 0) return ''
                                                      const researchArray = parentArray[0]?.[arrayProp.name]
                                                      if (!Array.isArray(researchArray) || researchArray.length <= 1) return ''
                                                      const isRowExpanded = isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)
                                                      return isRowExpanded ? '^' : `+${researchArray.length - 1}`
                                                    })()
                                                  }}
                                                </span>
                                              </div>
                                            </div>
                                          </template>
                                          <template v-else>
                                            <div 
                                              class="px-3 py-2 cursor-pointer hover:bg-gray-100 rounded flex items-center justify-between"
                                              :title="String(arrItem?.[subProp.name] ?? '')"
                                              @click.stop="toggleNestedArrayExpansion(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)"
                                            >
                                              <span class="truncate">{{ arrItem?.[subProp.name] ?? '-' }}</span>
                                              <span v-if="arrIdx === 0 && subProp.name === getDeepNestedProperties(header.field, nestedProp.name, arrayProp.name)[0].name" class="text-gray-500 ml-2 flex-shrink-0">
                                                {{ 
                                                  (() => {
                                                    const parentArray = item[header.field]?.[nestedProp.name]
                                                    if (!Array.isArray(parentArray) || parentArray.length === 0) return ''
                                                    const researchArray = parentArray[0]?.[arrayProp.name]
                                                    if (!Array.isArray(researchArray) || researchArray.length <= 1) return ''
                                                    const isRowExpanded = isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)
                                                    return isRowExpanded ? '^' : `+${researchArray.length - 1}`
                                                  })()
                                                }}
                                              </span>
                                            </div>
                                          </template>
                                        </div>
                                      </div>
                                    </div>
                                  </template>
                                </div>
                              </template>
                              <template v-else-if="arrayProp.isUnionArray && isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)">
                                <div class="flex flex-col">
                                  <div
                                    v-for="(arrItem, arrIdx) in (() => {
                                      const parentArray = item[header.field]?.[nestedProp.name]
                                      if (!Array.isArray(parentArray) || parentArray.length === 0) return []
                                      const researchArray = parentArray[0]?.[arrayProp.name]
                                      return Array.isArray(researchArray) ? researchArray : []
                                    })()"
                                    :key="arrIdx"
                                    class="px-3 py-2 text-xs cursor-pointer hover:bg-gray-100 border-b last:border-b-0 border-gray-200 flex items-center justify-between"
                                    @click="toggleNestedArrayExpansion(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)"
                                  >
                                    <span class="truncate" :title="String(arrItem?.type || arrItem || '')">{{ arrItem?.type || arrItem || '-' }}</span>
                                    <span v-if="arrIdx === 0" class="text-gray-500 ml-2 flex-shrink-0">^</span>
                                  </div>
                                </div>
                              </template>
                              <template v-else-if="arrayProp.type?.type === 'array' && !arrayProp.isUnionArray && isDeepNestedExpanded(header.field, nestedProp.name, arrayProp.name)">
                                <div class="flex w-full h-full">
                                  <div
                                    v-for="subProp in getDeepNestedProperties(header.field, nestedProp.name, arrayProp.name)"
                                    :key="`${header.field}.${nestedProp.name}.${arrayProp.name}.${subProp.name}`"
                                    class="px-2 py-2 border-r last:border-r-0 border-gray-300"
                                    :style="{ width: getDeepNestedSubColumnWidth(header.field, nestedProp.name, arrayProp.name, subProp.name) + 'px', minWidth: '100px' }"
                                  >
                                    <div 
                                      class="truncate"
                                      :title="String((() => {
                                        const parentArr = item[header.field]?.[nestedProp.name]
                                        if (!parentArr || parentArr.length === 0) return '-'
                                        const firstParent = parentArr[0]
                                        const deepArr = firstParent?.[arrayProp.name]
                                        if (!Array.isArray(deepArr) || deepArr.length === 0) return '-'
                                        const firstDeep = deepArr[0]
                                        return firstDeep?.[subProp.name] ?? '-'
                                      })())"
                                    >
                                      {{ 
                                        (() => {
                                          const parentArr = item[header.field]?.[nestedProp.name]
                                          if (!parentArr || parentArr.length === 0) return '-'
                                          const firstParent = parentArr[0]
                                          const deepArr = firstParent?.[arrayProp.name]
                                          if (!Array.isArray(deepArr) || deepArr.length === 0) return '-'
                                          const firstDeep = deepArr[0]
                                          return firstDeep?.[subProp.name] ?? '-'
                                        })()
                                      }}
                                    </div>
                                  </div>
                                </div>
                              </template>
                              <template v-else>
                                <div 
                                  class="px-2 py-2 truncate"
                                  :class="{ 'cursor-pointer hover:bg-gray-100 rounded': arrayProp.type?.type === 'array' }"
                                  :title="String(item[header.field]?.[nestedProp.name]?.[0]?.[arrayProp.name] ?? '')"
                                  @click="arrayProp.type?.type === 'array' ? toggleNestedArrayExpansion(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name) : null"
                                >
                                  {{ 
                                    (() => {
                                      const arrValue = item[header.field]?.[nestedProp.name]
                                      if (!arrValue || arrValue.length === 0) return '-'
                                      const firstItem = arrValue[0]
                                      const propValue = firstItem?.[arrayProp.name]
                                      if (Array.isArray(propValue)) {
                                        if (propValue.length === 0) return '[]'
                                        const firstSubItem = propValue[0]
                                        if (typeof firstSubItem !== 'object') {
                                          return propValue.length > 1 ? `${firstSubItem} +${propValue.length - 1}` : String(firstSubItem)
                                        }
                                        const displayValue = firstSubItem?.type || Object.values(firstSubItem)[0] || 'Item'
                                        return propValue.length > 1 ? `${displayValue} +${propValue.length - 1}` : String(displayValue)
                                      }
                                      return propValue ?? '-'
                                    })()
                                  }}
                                </div>
                              </template>
                            </div>
                          </div>
                        </template>
                        <template v-else-if="nestedProp.isObject && isNestedObjectExpanded(header.field, nestedProp.name)">
                          <div class="flex w-full h-full">
                            <div
                              v-for="deepProp in getNestedObjectProperties(header.field, nestedProp.name)"
                              :key="`${header.field}.${nestedProp.name}.${deepProp.name}`"
                              class="px-2 py-2 border-r last:border-r-0 border-gray-300"
                              :style="{ width: getNestedObjectSubColumnWidth(header.field, nestedProp.name, deepProp.name) + 'px', minWidth: '100px' }"
                            >
                              <div class="truncate" :title="String(item[header.field]?.[nestedProp.name]?.[deepProp.name] ?? '')">
                                {{ item[header.field]?.[nestedProp.name]?.[deepProp.name] ?? '-' }}
                              </div>
                            </div>
                          </div>
                        </template>
                        <template v-else>
                          <div 
                            class="px-2 py-2 truncate"
                            :title="String(item[header.field]?.[nestedProp.name] ?? '')"
                          >
                            {{ 
                              (() => {
                                const value = item[header.field]?.[nestedProp.name]
                                if (value === null || value === undefined) return '-'
                                    
                                    if (Array.isArray(value)) {
                                      if (value.length === 0) return '[]'
                                      const firstItem = value[0]
                                      if (typeof firstItem !== 'object') {
                                        return value.length > 1 ? `${firstItem} +${value.length - 1}` : String(firstItem)
                                      }
                                      const keys = Object.keys(firstItem)
                                      if (keys.length > 0) {
                                        const firstValue = firstItem[keys[0]]
                                        return value.length > 1 ? `${firstValue} +${value.length - 1}` : String(firstValue)
                                      }
                                      return value.length > 1 ? `Item +${value.length - 1}` : 'Item'
                                    }
                                    
                                    if (nestedProp.isObject && value) {
                                      const objKeys = Object.keys(value)
                                      return objKeys.length > 0 ? (value[objKeys[0]] ?? '{...}') : '{...}'
                                    }
                                    
                                    return value
                                  })()
                                }}
                          </div>
                        </template>
                      </div>
                    </div>
                  </template>
                </template>

                <template v-else>
                  <div
                    v-if="isRowCellExpanded(item.id, header.field) && header.isCollapsable"
                    class="bg-gray-50"
                  >
                    <div class="flex flex-col">
                      <template v-if="Array.isArray(item[header.field])">
                        <div
                          v-for="(arrItem, arrIdx) in item[header.field]"
                          :key="arrIdx"
                          class="p-2! text-xs cursor-pointer hover:bg-gray-100 border-b last:border-b-0 border-gray-200"
                          @click="toggleRowExpansion(item.id, header.field)"
                        >
                          <div class="px-2 flex items-center justify-between">
                            <template v-if="typeof arrItem !== 'object'">
                              <span class="truncate" :title="String(arrItem)">{{ arrItem }}</span>
                            </template>
                            <template v-else>
                              <span class="truncate" :title="String(arrItem?.name || arrItem?.title || JSON.stringify(arrItem))">{{ arrItem?.name || arrItem?.title || JSON.stringify(arrItem) }}</span>
                            </template>
                            <span v-if="arrIdx === 0" class="text-gray-500 ml-2 flex-shrink-0">^</span>
                          </div>
                        </div>
                      </template>
                      <div v-else-if="typeof item[header.field] === 'object'" class="text-xs truncate px-2 py-1" :title="JSON.stringify(item[header.field])">
                        {{ JSON.stringify(item[header.field]) }}
                      </div>
                    </div>
                  </div>

                  <div
                    v-else
                    class="px-2 py-2"
                  >
                    <div
                      :class="{ 'cursor-pointer hover:bg-gray-100 rounded px-1': header.isCollapsable }"
                      class="truncate"
                      :title="String(item[header.field] || '')"
                      @click="header.isCollapsable ? toggleRowExpansion(item.id, header.field) : null"
                    >
                      {{
                        isDateField(header.field)
                          ? formatDate(item[header.field])
                          : getCellDisplayValue(item, header.field)
                      }}
                    </div>
                  </div>
                </template>
                  </div>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>

      <div v-if="items.length === 0 && !loading" class="p-8 text-center">
        <Button label="No Data - Click To Search Again" @click="find" />
      </div>

      <div v-if="loading" class="absolute inset-0 bg-white bg-opacity-75 flex items-center justify-center">
        <div class="text-lg font-semibold">Loading...</div>
      </div>
    </div>

    <div class="border-t border-gray-300 p-3 flex items-center justify-between bg-white flex-shrink-0">
      <div class="text-sm text-gray-600">
        Showing {{ options.first + 1 }} to {{ Math.min(options.first + options.rows, totalItems) }} of {{ totalItems }} results
      </div>
      <div class="flex gap-2">
        <Button
          label="Previous"
          size="small"
          :disabled="options.first === 0"
          @click="onPage({ first: Math.max(0, options.first - options.rows), rows: options.rows })"
        />
        <Button
          label="Next"
          size="small"
          :disabled="options.first + options.rows >= totalItems"
          @click="onPage({ first: options.first + options.rows, rows: options.rows })"
        />
      </div>
    </div>
  </div>
</template>
<style>
.p-datatable .p-button {
  margin-top: 1rem;
}
.p-toolbar-start {
  width: 100% !important;
}

.truncate-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
  max-width: 100%;
}

.truncate {
  white-space: nowrap !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
}

.resize-handle {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 6px;
  cursor: col-resize;
  background: transparent;
  z-index: 10;
  transition: background-color 0.2s;
}

.resize-handle:hover {
  background-color: rgba(59, 130, 246, 0.5);
}

td > div {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

td > div.flex {
  overflow: visible;
}

td > div.flex > div {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

th {
  overflow: hidden;
}

th > div {
  overflow: hidden;
}
</style>