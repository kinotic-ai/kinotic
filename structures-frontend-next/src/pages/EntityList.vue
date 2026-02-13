
<script lang="ts">
import { Component, Vue, Prop } from 'vue-facing-decorator'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'

import { Pageable, type Page, Order, Direction, type Identifiable } from '@kinotic/continuum-client'
import { Structure, type IStructureService, Structures, type IEntitiesService } from '@kinotic/structures-api'
import { createDebug } from '@/util/debug'

const debug = createDebug('entity-list')

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
import {
  EntityTableToolbar,
  EntityPagination,
  EntityTableHeaders,
  EntityTableBody,
  ENTITY_LIST_INJECTION_KEY
} from '@/components/entity-list'
import { ExpansionStateManager } from '@/components/entity-list/ExpansionStateManager'
import { PropertyInspector } from '@/components/entity-list/PropertyInspector'
import { ColumnWidthCalculator } from '@/components/entity-list/ColumnWidthCalculator'
import { CellFormatter } from '@/components/entity-list/CellFormatter'
import Button from 'primevue/button'

@Component({
  components: {
    DataTable,
    Column,
    Button,
    EntityTableToolbar,
    EntityPagination,
    EntityTableHeaders,
    EntityTableBody
  },
  provide() {
    return {
      [ENTITY_LIST_INJECTION_KEY]: this
    }
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

  private _expansion = new ExpansionStateManager()
  private _inspector = new PropertyInspector()
  private _widthCalc = new ColumnWidthCalculator(this._expansion, this._inspector)
  private _formatter = new CellFormatter(this._inspector)

  resizingColumn: any = null
  startX = 0
  startWidth = 0
  wasExpanded = false
  resizeVersion = 0

  private _resizeRafId: number | null = null
  private _pendingResizeEvent: MouseEvent | null = null
  private _pendingResizeKind: 'column' | 'nested' | 'deep' | 'veryDeep' | 'ultraDeep' | null = null

  entitiesService: IEntitiesService = Structures.getEntitiesService()
  structureService: IStructureService = Structures.getStructureService()

  options = {
    rows: 50,
    first: 0,
    sortField: '',
    sortOrder: 1
  }

  get expandedColumns() { return this._expansion.expandedColumns }
  get expandedRows() { return this._expansion.expandedRows }
  get expandedNestedObjects() { return this._expansion.expandedNestedObjects }
  get expandedDeepNested() { return this._expansion.expandedDeepNested }
  get expandedVeryDeepNested() { return this._expansion.expandedVeryDeepNested }
  get expandedNestedArrays() { return this._expansion.expandedNestedArrays }

  toggleColumnExpansion(fieldName: string) { this._expansion.toggleColumnExpansion(fieldName) }
  toggleRowExpansion(rowId: string, fieldName: string) { this._expansion.toggleRowExpansion(rowId, fieldName) }
  toggleNestedObjectExpansion(fieldName: string, nestedProp: string) { this._expansion.toggleNestedObjectExpansion(fieldName, nestedProp) }
  toggleDeepNestedExpansion(fieldName: string, nestedProp: string, deepProp: string) { this._expansion.toggleDeepNestedExpansion(fieldName, nestedProp, deepProp) }
  toggleVeryDeepNestedExpansion(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string) { this._expansion.toggleVeryDeepNestedExpansion(fieldName, nestedProp, deepProp, veryDeepProp) }
  toggleNestedArrayExpansion(rowId: string, parentPath: string, arrayField: string) { this._expansion.toggleNestedArrayExpansion(rowId, parentPath, arrayField) }

  isColumnExpanded(fieldName: string): boolean { return this._expansion.isColumnExpanded(fieldName) }
  isRowCellExpanded(rowId: string, fieldName: string): boolean { return this._expansion.isRowCellExpanded(rowId, fieldName) }
  isNestedObjectExpanded(fieldName: string, nestedProp: string): boolean { return this._expansion.isNestedObjectExpanded(fieldName, nestedProp) }
  isDeepNestedExpanded(fieldName: string, nestedProp: string, deepProp: string): boolean { return this._expansion.isDeepNestedExpanded(fieldName, nestedProp, deepProp) }
  isVeryDeepNestedExpanded(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string): boolean { return this._expansion.isVeryDeepNestedExpanded(fieldName, nestedProp, deepProp, veryDeepProp) }
  isNestedArrayExpanded(rowId: string, parentPath: string, arrayField: string): boolean { return this._expansion.isNestedArrayExpanded(rowId, parentPath, arrayField) }

  isPrimitiveArray(fieldName: string): boolean { return this._inspector.isPrimitiveArray(fieldName) }
  isNestedPrimitiveArray(fieldName: string, nestedPropName: string): boolean { return this._inspector.isNestedPrimitiveArray(fieldName, nestedPropName) }
  isDeepNestedPrimitiveArray(fieldName: string, nestedProp: string, deepProp: string): boolean { return this._inspector.isDeepNestedPrimitiveArray(fieldName, nestedProp, deepProp) }
  getNestedProperties(fieldName: string): any[] { return this._inspector.getNestedProperties(fieldName) }
  getNestedObjectProperties(fieldName: string, nestedProp: string): any[] { return this._inspector.getNestedObjectProperties(fieldName, nestedProp) }
  getDeepNestedProperties(fieldName: string, nestedProp: string, deepProp: string): any[] { return this._inspector.getDeepNestedProperties(fieldName, nestedProp, deepProp) }
  getVeryDeepNestedProperties(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string): any[] { return this._inspector.getVeryDeepNestedProperties(fieldName, nestedProp, deepProp, veryDeepProp) }

  getExpandedColumnWidth(fieldName: string): number { void this.resizeVersion; return this._widthCalc.getExpandedColumnWidth(fieldName) }
  getNestedPropWidth(fieldName: string, nestedProp: any): number { void this.resizeVersion; return this._widthCalc.getNestedPropWidth(fieldName, nestedProp) }
  getNestedPropRenderWidth(fieldName: string, nestedProp: any, nestedPropIndex: number): number { void this.resizeVersion; return this._widthCalc.getNestedPropRenderWidth(fieldName, nestedProp, nestedPropIndex) }
  getNestedObjectSubColumnWidth(fieldName: string, nestedProp: string, subPropName: string): number { void this.resizeVersion; return this._widthCalc.getNestedObjectSubColumnWidth(fieldName, nestedProp, subPropName) }
  getDeepNestedSubColumnWidth(fieldName: string, nestedProp: string, arrayProp: string, subPropName: string): number { void this.resizeVersion; return this._widthCalc.getDeepNestedSubColumnWidth(fieldName, nestedProp, arrayProp, subPropName) }
  getUltraDeepNestedSubColumnWidth(fieldName: string, nestedProp: string, arrayProp: string, subArrayProp: string, ultraDeepPropName: string): number { void this.resizeVersion; return this._widthCalc.getUltraDeepNestedSubColumnWidth(fieldName, nestedProp, arrayProp, subArrayProp, ultraDeepPropName) }

  startColumnResize(event: MouseEvent, header: any) {
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

  private _scheduleResize(kind: 'column' | 'nested' | 'deep' | 'veryDeep' | 'ultraDeep', event: MouseEvent) {
    this._pendingResizeKind = kind
    this._pendingResizeEvent = event
    if (this._resizeRafId !== null) return

    this._resizeRafId = requestAnimationFrame(() => {
      this._resizeRafId = null
      const e = this._pendingResizeEvent
      const k = this._pendingResizeKind
      if (!e || !k) return
      this._applyPendingResize(k, e)
    })
  }

  private _applyPendingResize(kind: NonNullable<EntityList['_pendingResizeKind']>, event: MouseEvent) {
    switch (kind) {
      case 'column':
        this._applyColumnResize(event)
        break
      case 'nested':
        this._applyNestedColumnResize(event)
        break
      case 'deep':
        this._applyDeepNestedColumnResize(event)
        break
      case 'veryDeep':
        this._applyVeryDeepNestedColumnResize(event)
        break
      case 'ultraDeep':
        this._applyUltraDeepNestedColumnResize(event)
        break
    }
  }

  private _flushPendingResize(kind: EntityList['_pendingResizeKind']) {
    if (!kind) return
    if (this._resizeRafId !== null) {
      cancelAnimationFrame(this._resizeRafId)
      this._resizeRafId = null
    }
    const e = this._pendingResizeEvent
    const k = this._pendingResizeKind
    this._pendingResizeEvent = null
    this._pendingResizeKind = null
    if (e && k === kind) {
      this._applyPendingResize(k, e)
    }
  }

  private _applyColumnResize(event: MouseEvent) {
    if (!this.resizingColumn) return
    const diff = event.pageX - this.startX
    const newWidth = Math.max(50, this.startWidth + diff)
    const headerIndex = this.headers.findIndex((h: any) => h.field === this.resizingColumn.field)
    if (headerIndex === -1) return
    if (this.wasExpanded) {
      this.headers[headerIndex] = { ...this.headers[headerIndex], expandedWidth: newWidth }
    } else {
      this.headers[headerIndex] = { ...this.headers[headerIndex], width: newWidth }
    }
    this.resizingColumn = this.headers[headerIndex]
    this.resizeVersion++
  }

  onColumnResize(event: MouseEvent) {
    this._scheduleResize('column', event)
  }

  stopColumnResize() {
    this._flushPendingResize('column')
    document.removeEventListener('mousemove', this.onColumnResize)
    document.removeEventListener('mouseup', this.stopColumnResize)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    this.resizingColumn = null
  }

  startNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: any) {
    this.resizingColumn = { parentField, nestedProp }
    this.startX = event.pageX
    this.startWidth = this.getNestedPropWidth(parentField, nestedProp)
    document.addEventListener('mousemove', this.onNestedColumnResize)
    document.addEventListener('mouseup', this.stopNestedColumnResize)
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }

  private _applyNestedColumnResize(event: MouseEvent) {
    if (!this.resizingColumn || !this.resizingColumn.nestedProp) return
    const diff = event.pageX - this.startX
    const newWidth = Math.max(80, this.startWidth + diff)
    const key = `${this.resizingColumn.parentField}.${this.resizingColumn.nestedProp.name}`
    this._widthCalc.nestedColumnWidths.set(key, newWidth)
    const parentField = this.resizingColumn.parentField
    const headerIndex = this.headers.findIndex((h: any) => h.field === parentField)
    if (headerIndex !== -1) {
      const nestedProps = this.getNestedProperties(parentField)
      let totalWidth = 0
      for (const prop of nestedProps) {
        totalWidth += this._widthCalc.getNestedPropWidth(parentField, prop)
      }
      this.headers[headerIndex] = { ...this.headers[headerIndex], expandedWidth: totalWidth }
    }
    this.resizeVersion++
  }

  onNestedColumnResize(event: MouseEvent) {
    this._scheduleResize('nested', event)
  }

  stopNestedColumnResize() {
    this._flushPendingResize('nested')
    document.removeEventListener('mousemove', this.onNestedColumnResize)
    document.removeEventListener('mouseup', this.stopNestedColumnResize)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    this.resizingColumn = null
  }

  startDeepNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: string, deepProp: any) {
    this.resizingColumn = { parentField, nestedProp, deepProp, level: 'deep' }
    this.startX = event.pageX
    this.startWidth = this.getNestedObjectSubColumnWidth(parentField, nestedProp, deepProp.name)
    document.addEventListener('mousemove', this.onDeepNestedColumnResize)
    document.addEventListener('mouseup', this.stopDeepNestedColumnResize)
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }

  private _applyDeepNestedColumnResize(event: MouseEvent) {
    if (!this.resizingColumn || this.resizingColumn.level !== 'deep') return
    const diff = event.pageX - this.startX
    const newWidth = Math.max(80, this.startWidth + diff)
    const key = `${this.resizingColumn.parentField}.${this.resizingColumn.nestedProp}.${this.resizingColumn.deepProp.name}`
    this._widthCalc.deepNestedColumnWidths.set(key, newWidth)
    const nestedKey = `${this.resizingColumn.parentField}.${this.resizingColumn.nestedProp}`
    const parentField = this.resizingColumn.parentField
    const nestedPropName = this.resizingColumn.nestedProp
    const deepProps = this.getNestedObjectProperties(parentField, nestedPropName)
    let totalWidth = 0
    for (const prop of deepProps) {
      totalWidth += this._widthCalc.getNestedObjectSubColumnWidth(parentField, nestedPropName, prop.name)
    }
    this._widthCalc.nestedColumnWidths.set(nestedKey, totalWidth)
    const headerIndex = this.headers.findIndex((h: any) => h.field === parentField)
    if (headerIndex !== -1) {
      const nestedProps = this.getNestedProperties(parentField)
      let parentTotalWidth = 0
      for (const prop of nestedProps) {
        parentTotalWidth += this._widthCalc.getNestedPropWidth(parentField, prop)
      }
      this.headers[headerIndex] = { ...this.headers[headerIndex], expandedWidth: parentTotalWidth, width: parentTotalWidth }
    }
    this.resizeVersion++
  }

  onDeepNestedColumnResize(event: MouseEvent) {
    this._scheduleResize('deep', event)
  }

  stopDeepNestedColumnResize() {
    this._flushPendingResize('deep')
    document.removeEventListener('mousemove', this.onDeepNestedColumnResize)
    document.removeEventListener('mouseup', this.stopDeepNestedColumnResize)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    this.resizingColumn = null
  }

  startVeryDeepNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: string, deepProp: string, veryDeepProp: any) {
    this.resizingColumn = { parentField, nestedProp, deepProp, veryDeepProp, level: 'veryDeep' }
    this.startX = event.pageX
    this.startWidth = this.getDeepNestedSubColumnWidth(parentField, nestedProp, deepProp, veryDeepProp.name)
    document.addEventListener('mousemove', this.onVeryDeepNestedColumnResize)
    document.addEventListener('mouseup', this.stopVeryDeepNestedColumnResize)
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }

  private _applyVeryDeepNestedColumnResize(event: MouseEvent) {
    if (!this.resizingColumn || this.resizingColumn.level !== 'veryDeep') return
    const diff = event.pageX - this.startX
    const newWidth = Math.max(80, this.startWidth + diff)
    const key = `${this.resizingColumn.parentField}.${this.resizingColumn.nestedProp}.${this.resizingColumn.deepProp}.${this.resizingColumn.veryDeepProp.name}`
    this._widthCalc.veryDeepNestedColumnWidths.set(key, newWidth)
    this.updateParentWidthsAfterDeepResize(
      this.resizingColumn.parentField,
      this.resizingColumn.nestedProp,
      this.resizingColumn.deepProp
    )
    this.resizeVersion++
  }

  onVeryDeepNestedColumnResize(event: MouseEvent) {
    this._scheduleResize('veryDeep', event)
  }

  stopVeryDeepNestedColumnResize() {
    this._flushPendingResize('veryDeep')
    document.removeEventListener('mousemove', this.onVeryDeepNestedColumnResize)
    document.removeEventListener('mouseup', this.stopVeryDeepNestedColumnResize)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    this.resizingColumn = null
  }

  startUltraDeepNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: string, deepProp: string, veryDeepProp: string, ultraDeepProp: any) {
    this.resizingColumn = { parentField, nestedProp, deepProp, veryDeepProp, ultraDeepProp, level: 'ultraDeep' }
    this.startX = event.pageX
    this.startWidth = this.getUltraDeepNestedSubColumnWidth(parentField, nestedProp, deepProp, veryDeepProp, ultraDeepProp.name)
    document.addEventListener('mousemove', this.onUltraDeepNestedColumnResize)
    document.addEventListener('mouseup', this.stopUltraDeepNestedColumnResize)
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }

  private _applyUltraDeepNestedColumnResize(event: MouseEvent) {
    if (!this.resizingColumn || this.resizingColumn.level !== 'ultraDeep') return
    const diff = event.pageX - this.startX
    const newWidth = Math.max(80, this.startWidth + diff)
    const key = `${this.resizingColumn.parentField}.${this.resizingColumn.nestedProp}.${this.resizingColumn.deepProp}.${this.resizingColumn.veryDeepProp}.${this.resizingColumn.ultraDeepProp.name}`
    this._widthCalc.ultraDeepNestedColumnWidths.set(key, newWidth)
    this.updateParentWidthsAfterDeepResize(
      this.resizingColumn.parentField,
      this.resizingColumn.nestedProp,
      this.resizingColumn.deepProp
    )
    this.resizeVersion++
  }

  onUltraDeepNestedColumnResize(event: MouseEvent) {
    this._scheduleResize('ultraDeep', event)
  }

  stopUltraDeepNestedColumnResize() {
    this._flushPendingResize('ultraDeep')
    document.removeEventListener('mousemove', this.onUltraDeepNestedColumnResize)
    document.removeEventListener('mouseup', this.stopUltraDeepNestedColumnResize)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    this.resizingColumn = null
  }

  updateParentWidthsAfterDeepResize(parentField: string, nestedProp: string, deepProp: string) {
    const deepKey = `${parentField}.${nestedProp}.${deepProp}`
    const veryDeepProps = this.getDeepNestedProperties(parentField, nestedProp, deepProp)
    let deepTotalWidth = 0
    for (const prop of veryDeepProps) {
      if (prop.type?.type === 'array' && this.isVeryDeepNestedExpanded(parentField, nestedProp, deepProp, prop.name)) {
        const ultraProps = this.getVeryDeepNestedProperties(parentField, nestedProp, deepProp, prop.name)
        deepTotalWidth += ultraProps.reduce(
          (sum: number, up: any) => sum + this._widthCalc.getUltraDeepNestedSubColumnWidth(parentField, nestedProp, deepProp, prop.name, up.name),
          0
        )
      } else {
        deepTotalWidth += this._widthCalc.getDeepNestedSubColumnWidth(parentField, nestedProp, deepProp, prop.name)
      }
    }
    this._widthCalc.deepNestedColumnWidths.set(deepKey, deepTotalWidth)
    const nestedKey = `${parentField}.${nestedProp}`
    const deepProps = this.getNestedObjectProperties(parentField, nestedProp)
    let nestedTotalWidth = 0
    for (const prop of deepProps) {
      nestedTotalWidth += this._widthCalc.getNestedObjectSubColumnWidth(parentField, nestedProp, prop.name)
    }
    this._widthCalc.nestedColumnWidths.set(nestedKey, nestedTotalWidth)
    const headerIndex = this.headers.findIndex((h: any) => h.field === parentField)
    if (headerIndex !== -1) {
      const nestedProps = this.getNestedProperties(parentField)
      let parentTotalWidth = 0
      for (const prop of nestedProps) {
        parentTotalWidth += this._widthCalc.getNestedPropWidth(parentField, prop)
      }
      this.headers[headerIndex] = { ...this.headers[headerIndex], expandedWidth: parentTotalWidth }
    }
    this.resizeVersion++
  }

  getCellDisplayValue(data: any, field: string): string { return this._formatter.getCellDisplayValue(data, field) }
  getCellTitleValue(data: any, field: string): string { return this._formatter.getCellTitleValue(data, field) }
  getArrayObjectLabel(obj: any, fieldName?: string): string { return this._formatter.getArrayObjectLabel(obj, fieldName) }
  getArrayPrimaryNestedProp(fieldName: string): string | null { return this._formatter.getArrayPrimaryNestedProp(fieldName) }

  formatDate(date: string): string { return DatetimeUtil.formatDate(date) }
  isDateField(field: string): boolean { return this._inspector.isDateField(field) }
  get rowColors() { return rowColors }

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

        this._inspector.setStructureProperties(this.structureProperties)

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
              expandedWidth: isComplex ? 600 : null
            }
            this.headers.push(headerDef)
            this.keys.push(property.name)
          }
        }

        this._widthCalc.setHeaders(this.headers)

        this.find()
      })
      .catch((error) => {
        debug('Error during structure retrieval: %O', error)
        this.displayAlert(error.message)
      })
  }

  beforeUnmount() {
    this._flushPendingResize(this._pendingResizeKind)
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
    debug('Alert displayed: %s', text)
    window.alert(text)
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

        this._inspector.setItems(this.items)
        this._widthCalc.setItems(this.items)

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
    <EntityTableToolbar 
      v-model:searchText="searchText"
      @search="search"
      @clearSearch="clearSearch"
    />

    <div class="flex-1 overflow-auto relative" style="min-height: 0;">
      <table class="w-full border-collapse table-fixed" style="box-sizing: border-box;">
        <EntityTableHeaders />
        <EntityTableBody />
      </table>

      <div v-if="items.length === 0 && !loading" class="p-8 text-center">
        <Button label="No Data - Click To Search Again" @click="find" />
      </div>

      <div v-if="loading" class="absolute inset-0 bg-white bg-opacity-75 flex items-center justify-center">
        <div class="text-lg font-semibold">Loading...</div>
      </div>
    </div>

    <EntityPagination
      :first="options.first"
      :rows="options.rows"
      :totalItems="totalItems"
      @page="onPage"
    />
  </div>
</template>
<style scoped>
.p-datatable .p-button {
  margin-top: 1rem;
}
.p-toolbar-start {
  width: 100% !important;
}

:deep(.truncate-text) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
  max-width: 100%;
  min-width: 0;
  flex: 1 1 auto;
}

:deep(.truncate) {
  white-space: nowrap !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
  display: block;
  max-width: 100%;
  min-width: 0;
  flex: 1 1 auto;
}

:deep(.resize-handle) {
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

:deep(.resize-handle:hover) {
  background-color: rgba(59, 130, 246, 0.5);
}

:deep(td > div) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(td > div.flex) {
  overflow: hidden;
  min-width: 0;
}

:deep(td > div.flex > div) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

:deep(th) {
  overflow: hidden;
  border-top: none !important;
  border-bottom: none !important;
}

:deep(th > div) {
  overflow: hidden;
  border-top: none !important;
  border-bottom: none !important;
}
</style>
