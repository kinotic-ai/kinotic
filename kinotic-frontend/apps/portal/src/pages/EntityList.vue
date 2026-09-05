
<script setup lang="ts">
import { ref, reactive, provide, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'

import { Pageable, type Page, Order, Direction, type Identifiable } from '@kinotic-ai/core'
import { Kinotic } from '@kinotic-ai/core'
import { EntityDefinition, type IEntityDefinitionService } from '@kinotic-ai/management-api'
import { type IEntitiesRepository } from '@kinotic-ai/persistence'
import { createDebug } from '@kinotic-ai/frontend-common'

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

import { DatetimeUtil } from '@kinotic-ai/frontend-common'
import { EntityDefinitionUtil } from '@/util/EntityDefinitionUtil'
import { rowColors } from '@/util/rowColors'
import {
  EntityTableToolbar,
  EntityPagination,
  EntityTableHeaders,
  EntityTableBody,
  ENTITY_LIST_INJECTION_KEY,
  type EntityListContext
} from '@/components/entity-list'
import { ExpansionStateManager } from '@/components/entity-list/ExpansionStateManager'
import { PropertyInspector } from '@/components/entity-list/PropertyInspector'
import { ColumnWidthCalculator } from '@/components/entity-list/ColumnWidthCalculator'
import { CellFormatter } from '@/components/entity-list/CellFormatter'
import Button from 'primevue/button'
import { isDark as darkMode } from '@kinotic-ai/frontend-common'

const props = defineProps<{
  entityDefinitionId?: string
}>()

const route = useRoute()

const loading = ref(false)
const finishedInitialLoad = ref(false)
const items = ref<EntityItem[]>([])
const totalItems = ref(0)
const searchText = ref<string | null>(null)

const keys = ref<string[]>([])
const headers = ref<HeaderDef[]>([])
const entityDefinitionProperties = ref<any>({})
const entityDefinition = ref<EntityDefinition>()

// Child components render from expansion/inspector state through the provided context,
// so these managers are reactive proxies to keep those reads tracked. The cast restores
// the class type, which Reactive<T>'s mapped type erases (TS-private members are dropped).
const _expansion = reactive(new ExpansionStateManager()) as ExpansionStateManager
const _inspector = reactive(new PropertyInspector()) as PropertyInspector
const _widthCalc = new ColumnWidthCalculator(_expansion, _inspector)
const _formatter = new CellFormatter(_inspector)

const resizingColumn = ref<any>(null)
const startX = ref(0)
const startWidth = ref(0)
const wasExpanded = ref(false)
const resizeVersion = ref(0)

type ResizeKind = 'column' | 'nested' | 'deep' | 'veryDeep' | 'ultraDeep'

const _resizeRafId = ref<number | null>(null)
const _pendingResizeEvent = ref<MouseEvent | null>(null)
const _pendingResizeKind = ref<ResizeKind | null>(null)

const entitiesService: IEntitiesRepository = Kinotic.entities
const entityDefinitionService: IEntityDefinitionService = Kinotic.entityDefinitions

const options = ref({
  rows: 50,
  first: 0,
  sortField: '',
  sortOrder: 1
})

const isDark = darkMode

function togglePathExpansion(...path: string[]) { _expansion.togglePathExpansion(...path) }
function isPathExpanded(...path: string[]): boolean { return _expansion.isPathExpanded(...path) }

function toggleColumnExpansion(fieldName: string) { _expansion.toggleColumnExpansion(fieldName) }
function toggleRowExpansion(rowId: string, fieldName: string) { _expansion.toggleRowExpansion(rowId, fieldName) }
function toggleNestedObjectExpansion(fieldName: string, nestedProp: string) { _expansion.toggleNestedObjectExpansion(fieldName, nestedProp) }
function toggleDeepNestedExpansion(fieldName: string, nestedProp: string, deepProp: string) { _expansion.toggleDeepNestedExpansion(fieldName, nestedProp, deepProp) }
function toggleVeryDeepNestedExpansion(fieldName: string, nestedProp: string, deepProp: string, ...veryDeepPath: string[]) { _expansion.toggleVeryDeepNestedExpansion(fieldName, nestedProp, deepProp, ...veryDeepPath) }
function toggleNestedArrayExpansion(rowId: string, parentPath: string, arrayField: string) { _expansion.toggleNestedArrayExpansion(rowId, parentPath, arrayField) }

function isColumnExpanded(fieldName: string): boolean { return _expansion.isColumnExpanded(fieldName) }
function isRowCellExpanded(rowId: string, fieldName: string): boolean { return _expansion.isRowCellExpanded(rowId, fieldName) }
function isNestedObjectExpanded(fieldName: string, nestedProp: string): boolean { return _expansion.isNestedObjectExpanded(fieldName, nestedProp) }
function isDeepNestedExpanded(fieldName: string, nestedProp: string, deepProp: string): boolean { return _expansion.isDeepNestedExpanded(fieldName, nestedProp, deepProp) }
function isVeryDeepNestedExpanded(fieldName: string, nestedProp: string, deepProp: string, ...veryDeepPath: string[]): boolean { return _expansion.isVeryDeepNestedExpanded(fieldName, nestedProp, deepProp, ...veryDeepPath) }
function isNestedArrayExpanded(rowId: string, parentPath: string, arrayField: string): boolean { return _expansion.isNestedArrayExpanded(rowId, parentPath, arrayField) }

function isPrimitiveArrayAtPath(...path: string[]): boolean { return _inspector.isPrimitiveArrayAtPath(...path) }
function getPropertiesAtPath(...path: string[]): any[] { return _inspector.getPropertiesAtPath(...path) }
function isPrimitiveArray(fieldName: string): boolean { return _inspector.isPrimitiveArray(fieldName) }
function isNestedPrimitiveArray(fieldName: string, nestedPropName: string): boolean { return _inspector.isNestedPrimitiveArray(fieldName, nestedPropName) }
function isDeepNestedPrimitiveArray(fieldName: string, nestedProp: string, deepProp: string): boolean { return _inspector.isDeepNestedPrimitiveArray(fieldName, nestedProp, deepProp) }
function getNestedProperties(fieldName: string): any[] { return _inspector.getNestedProperties(fieldName) }
function getNestedObjectProperties(fieldName: string, nestedProp: string): any[] { return _inspector.getNestedObjectProperties(fieldName, nestedProp) }
function getDeepNestedProperties(fieldName: string, nestedProp: string, deepProp: string): any[] { return _inspector.getDeepNestedProperties(fieldName, nestedProp, deepProp) }
function getVeryDeepNestedProperties(fieldName: string, nestedProp: string, deepProp: string, veryDeepProp: string): any[] { return _inspector.getVeryDeepNestedProperties(fieldName, nestedProp, deepProp, veryDeepProp) }

function getExpandedColumnWidth(fieldName: string): number { void resizeVersion.value; return _widthCalc.getExpandedColumnWidth(fieldName) }
function getNestedPropWidth(fieldName: string, nestedProp: any): number { void resizeVersion.value; return _widthCalc.getNestedPropWidth(fieldName, nestedProp) }
function getNestedPropRenderWidth(fieldName: string, nestedProp: any, nestedPropIndex: number): number { void resizeVersion.value; return _widthCalc.getNestedPropRenderWidth(fieldName, nestedProp, nestedPropIndex) }
function getNestedObjectSubColumnWidth(fieldName: string, nestedProp: string, subPropName: string): number { void resizeVersion.value; return _widthCalc.getNestedObjectSubColumnWidth(fieldName, nestedProp, subPropName) }
function getDeepNestedSubColumnWidth(fieldName: string, nestedProp: string, arrayProp: string, subPropName: string): number { void resizeVersion.value; return _widthCalc.getDeepNestedSubColumnWidth(fieldName, nestedProp, arrayProp, subPropName) }
function getUltraDeepNestedSubColumnWidth(fieldName: string, nestedProp: string, arrayProp: string, subArrayProp: string, ultraDeepPropName: string): number { void resizeVersion.value; return _widthCalc.getUltraDeepNestedSubColumnWidth(fieldName, nestedProp, arrayProp, subArrayProp, ultraDeepPropName) }
function getWidthAtPath(...path: string[]): number { void resizeVersion.value; return _widthCalc.getWidthAtPath(...path) }

function startPathResize(event: MouseEvent, path: string[], prop: any) {

  resizingColumn.value = { path, prop, level: 'path' }
  startX.value = event.pageX
  startWidth.value = _widthCalc.getWidthAtPath(...path, prop.name)
  const onMove = (e: MouseEvent) => {
    const diff = e.pageX - startX.value
    const newWidth = Math.max(80, startWidth.value + diff)
    _widthCalc.columnWidths.set([...path, prop.name].join('.'), newWidth)
    _recalcParentWidths(path)
    resizeVersion.value++
  }
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    resizingColumn.value = null
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function _recalcParentWidths(path: string[]) {
  for (let depth = path.length; depth >= 2; depth--) {
    const parentPath = path.slice(0, depth)
    const parentKey = parentPath.join('.')
    const childProps = getPropertiesAtPath(...parentPath)
    let total = 0
    for (const child of childProps) {
      const childPath = [...parentPath, child.name]
      if ((child.isObject || child.isArray) && isPathExpanded(...childPath)) {
        total += _widthCalc.getWidthAtPath(...childPath)
      } else {
        total += _widthCalc.getWidthAtPath(...childPath)
      }
    }
    _widthCalc.columnWidths.set(parentKey, total)
  }
  const headerField = path[0]
  const headerIndex = headers.value.findIndex((h: any) => h.field === headerField)
  if (headerIndex !== -1) {
    const nestedProps = getPropertiesAtPath(headerField)
    let parentTotalWidth = 0
    for (const prop of nestedProps) {
      parentTotalWidth += _widthCalc.getNestedPropWidth(headerField, prop)
    }
    headers.value[headerIndex] = { ...headers.value[headerIndex], expandedWidth: parentTotalWidth }
  }
}

function startColumnResize(event: MouseEvent, header: any) {
  resizingColumn.value = header
  startX.value = event.pageX
  wasExpanded.value = isColumnExpanded(header.field)
  startWidth.value = wasExpanded.value
    ? getExpandedColumnWidth(header.field)
    : header.width
  document.addEventListener('mousemove', onColumnResize)
  document.addEventListener('mouseup', stopColumnResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function _scheduleResize(kind: ResizeKind, event: MouseEvent) {
  _pendingResizeKind.value = kind
  _pendingResizeEvent.value = event
  if (_resizeRafId.value !== null) return

  _resizeRafId.value = requestAnimationFrame(() => {
    _resizeRafId.value = null
    const e = _pendingResizeEvent.value
    const k = _pendingResizeKind.value
    if (!e || !k) return
    _applyPendingResize(k, e)
  })
}

function _applyPendingResize(kind: ResizeKind, event: MouseEvent) {
  switch (kind) {
    case 'column':
      _applyColumnResize(event)
      break
    case 'nested':
      _applyNestedColumnResize(event)
      break
    case 'deep':
      _applyDeepNestedColumnResize(event)
      break
    case 'veryDeep':
      _applyVeryDeepNestedColumnResize(event)
      break
    case 'ultraDeep':
      _applyUltraDeepNestedColumnResize(event)
      break
  }
}

function _flushPendingResize(kind: ResizeKind | null) {
  if (!kind) return
  if (_resizeRafId.value !== null) {
    cancelAnimationFrame(_resizeRafId.value)
    _resizeRafId.value = null
  }
  const e = _pendingResizeEvent.value
  const k = _pendingResizeKind.value
  _pendingResizeEvent.value = null
  _pendingResizeKind.value = null
  if (e && k === kind) {
    _applyPendingResize(k, e)
  }
}

function _applyColumnResize(event: MouseEvent) {
  if (!resizingColumn.value) return
  const diff = event.pageX - startX.value
  const newWidth = Math.max(50, startWidth.value + diff)
  const headerIndex = headers.value.findIndex((h: any) => h.field === resizingColumn.value.field)
  if (headerIndex === -1) return
  if (wasExpanded.value) {
    headers.value[headerIndex] = { ...headers.value[headerIndex], expandedWidth: newWidth }
  } else {
    headers.value[headerIndex] = { ...headers.value[headerIndex], width: newWidth }
  }
  resizingColumn.value = headers.value[headerIndex]
  resizeVersion.value++
}

function onColumnResize(event: MouseEvent) {
  _scheduleResize('column', event)
}

function stopColumnResize() {
  _flushPendingResize('column')
  document.removeEventListener('mousemove', onColumnResize)
  document.removeEventListener('mouseup', stopColumnResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  resizingColumn.value = null
}

function startNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: any) {
  resizingColumn.value = { parentField, nestedProp }
  startX.value = event.pageX
  startWidth.value = getNestedPropWidth(parentField, nestedProp)
  document.addEventListener('mousemove', onNestedColumnResize)
  document.addEventListener('mouseup', stopNestedColumnResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function _applyNestedColumnResize(event: MouseEvent) {
  if (!resizingColumn.value || !resizingColumn.value.nestedProp) return
  const diff = event.pageX - startX.value
  const newWidth = Math.max(80, startWidth.value + diff)
  const key = `${resizingColumn.value.parentField}.${resizingColumn.value.nestedProp.name}`
  _widthCalc.nestedColumnWidths.set(key, newWidth)
  const parentField = resizingColumn.value.parentField
  const headerIndex = headers.value.findIndex((h: any) => h.field === parentField)
  if (headerIndex !== -1) {
    const nestedProps = getNestedProperties(parentField)
    let totalWidth = 0
    for (const prop of nestedProps) {
      totalWidth += _widthCalc.getNestedPropWidth(parentField, prop)
    }
    headers.value[headerIndex] = { ...headers.value[headerIndex], expandedWidth: totalWidth }
  }
  resizeVersion.value++
}

function onNestedColumnResize(event: MouseEvent) {
  _scheduleResize('nested', event)
}

function stopNestedColumnResize() {
  _flushPendingResize('nested')
  document.removeEventListener('mousemove', onNestedColumnResize)
  document.removeEventListener('mouseup', stopNestedColumnResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  resizingColumn.value = null
}

function startDeepNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: string, deepProp: any) {
  resizingColumn.value = { parentField, nestedProp, deepProp, level: 'deep' }
  startX.value = event.pageX
  startWidth.value = getNestedObjectSubColumnWidth(parentField, nestedProp, deepProp.name)
  document.addEventListener('mousemove', onDeepNestedColumnResize)
  document.addEventListener('mouseup', stopDeepNestedColumnResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function _applyDeepNestedColumnResize(event: MouseEvent) {
  if (!resizingColumn.value || resizingColumn.value.level !== 'deep') return
  const diff = event.pageX - startX.value
  const newWidth = Math.max(80, startWidth.value + diff)
  const key = `${resizingColumn.value.parentField}.${resizingColumn.value.nestedProp}.${resizingColumn.value.deepProp.name}`
  _widthCalc.deepNestedColumnWidths.set(key, newWidth)
  const nestedKey = `${resizingColumn.value.parentField}.${resizingColumn.value.nestedProp}`
  const parentField = resizingColumn.value.parentField
  const nestedPropName = resizingColumn.value.nestedProp
  const deepProps = getNestedObjectProperties(parentField, nestedPropName)
  let totalWidth = 0
  for (const prop of deepProps) {
    totalWidth += _widthCalc.getNestedObjectSubColumnWidth(parentField, nestedPropName, prop.name)
  }
  _widthCalc.nestedColumnWidths.set(nestedKey, totalWidth)
  const headerIndex = headers.value.findIndex((h: any) => h.field === parentField)
  if (headerIndex !== -1) {
    const nestedProps = getNestedProperties(parentField)
    let parentTotalWidth = 0
    for (const prop of nestedProps) {
      parentTotalWidth += _widthCalc.getNestedPropWidth(parentField, prop)
    }
    headers.value[headerIndex] = { ...headers.value[headerIndex], expandedWidth: parentTotalWidth, width: parentTotalWidth }
  }
  resizeVersion.value++
}

function onDeepNestedColumnResize(event: MouseEvent) {
  _scheduleResize('deep', event)
}

function stopDeepNestedColumnResize() {
  _flushPendingResize('deep')
  document.removeEventListener('mousemove', onDeepNestedColumnResize)
  document.removeEventListener('mouseup', stopDeepNestedColumnResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  resizingColumn.value = null
}

function startVeryDeepNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: string, deepProp: string, veryDeepProp: any) {
  resizingColumn.value = { parentField, nestedProp, deepProp, veryDeepProp, level: 'veryDeep' }
  startX.value = event.pageX
  startWidth.value = getDeepNestedSubColumnWidth(parentField, nestedProp, deepProp, veryDeepProp.name)
  document.addEventListener('mousemove', onVeryDeepNestedColumnResize)
  document.addEventListener('mouseup', stopVeryDeepNestedColumnResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function _applyVeryDeepNestedColumnResize(event: MouseEvent) {
  if (!resizingColumn.value || resizingColumn.value.level !== 'veryDeep') return
  const diff = event.pageX - startX.value
  const newWidth = Math.max(80, startWidth.value + diff)
  const key = `${resizingColumn.value.parentField}.${resizingColumn.value.nestedProp}.${resizingColumn.value.deepProp}.${resizingColumn.value.veryDeepProp.name}`
  _widthCalc.veryDeepNestedColumnWidths.set(key, newWidth)
  updateParentWidthsAfterDeepResize(
    resizingColumn.value.parentField,
    resizingColumn.value.nestedProp,
    resizingColumn.value.deepProp
  )
  resizeVersion.value++
}

function onVeryDeepNestedColumnResize(event: MouseEvent) {
  _scheduleResize('veryDeep', event)
}

function stopVeryDeepNestedColumnResize() {
  _flushPendingResize('veryDeep')
  document.removeEventListener('mousemove', onVeryDeepNestedColumnResize)
  document.removeEventListener('mouseup', stopVeryDeepNestedColumnResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  resizingColumn.value = null
}

function startUltraDeepNestedColumnResize(event: MouseEvent, parentField: string, nestedProp: string, deepProp: string, veryDeepProp: string, ultraDeepProp: any) {
  resizingColumn.value = { parentField, nestedProp, deepProp, veryDeepProp, ultraDeepProp, level: 'ultraDeep' }
  startX.value = event.pageX
  startWidth.value = getUltraDeepNestedSubColumnWidth(parentField, nestedProp, deepProp, veryDeepProp, ultraDeepProp.name)
  document.addEventListener('mousemove', onUltraDeepNestedColumnResize)
  document.addEventListener('mouseup', stopUltraDeepNestedColumnResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function _applyUltraDeepNestedColumnResize(event: MouseEvent) {
  if (!resizingColumn.value || resizingColumn.value.level !== 'ultraDeep') return
  const diff = event.pageX - startX.value
  const newWidth = Math.max(80, startWidth.value + diff)
  const key = `${resizingColumn.value.parentField}.${resizingColumn.value.nestedProp}.${resizingColumn.value.deepProp}.${resizingColumn.value.veryDeepProp}.${resizingColumn.value.ultraDeepProp.name}`
  _widthCalc.ultraDeepNestedColumnWidths.set(key, newWidth)
  updateParentWidthsAfterDeepResize(
    resizingColumn.value.parentField,
    resizingColumn.value.nestedProp,
    resizingColumn.value.deepProp
  )
  resizeVersion.value++
}

function onUltraDeepNestedColumnResize(event: MouseEvent) {
  _scheduleResize('ultraDeep', event)
}

function stopUltraDeepNestedColumnResize() {
  _flushPendingResize('ultraDeep')
  document.removeEventListener('mousemove', onUltraDeepNestedColumnResize)
  document.removeEventListener('mouseup', stopUltraDeepNestedColumnResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  resizingColumn.value = null
}

function updateParentWidthsAfterDeepResize(parentField: string, nestedProp: string, deepProp: string) {
  const deepKey = `${parentField}.${nestedProp}.${deepProp}`
  const veryDeepProps = getDeepNestedProperties(parentField, nestedProp, deepProp)
  let deepTotalWidth = 0
  for (const prop of veryDeepProps) {
    if (prop.type?.type === 'array' && isVeryDeepNestedExpanded(parentField, nestedProp, deepProp, prop.name)) {
      const ultraProps = getVeryDeepNestedProperties(parentField, nestedProp, deepProp, prop.name)
      deepTotalWidth += ultraProps.reduce(
        (sum: number, up: any) => sum + _widthCalc.getUltraDeepNestedSubColumnWidth(parentField, nestedProp, deepProp, prop.name, up.name),
        0
      )
    } else {
      deepTotalWidth += _widthCalc.getDeepNestedSubColumnWidth(parentField, nestedProp, deepProp, prop.name)
    }
  }
  _widthCalc.deepNestedColumnWidths.set(deepKey, deepTotalWidth)
  const nestedKey = `${parentField}.${nestedProp}`
  const deepProps = getNestedObjectProperties(parentField, nestedProp)
  let nestedTotalWidth = 0
  for (const prop of deepProps) {
    nestedTotalWidth += _widthCalc.getNestedObjectSubColumnWidth(parentField, nestedProp, prop.name)
  }
  _widthCalc.nestedColumnWidths.set(nestedKey, nestedTotalWidth)
  const headerIndex = headers.value.findIndex((h: any) => h.field === parentField)
  if (headerIndex !== -1) {
    const nestedProps = getNestedProperties(parentField)
    let parentTotalWidth = 0
    for (const prop of nestedProps) {
      parentTotalWidth += _widthCalc.getNestedPropWidth(parentField, prop)
    }
    headers.value[headerIndex] = { ...headers.value[headerIndex], expandedWidth: parentTotalWidth }
  }
  resizeVersion.value++
}

function getCellDisplayValue(data: any, field: string): string { return _formatter.getCellDisplayValue(data, field) }
function getCellTitleValue(data: any, field: string): string { return _formatter.getCellTitleValue(data, field) }
function getArrayObjectLabel(obj: any, fieldName?: string): string { return _formatter.getArrayObjectLabel(obj, fieldName) }
function getArrayPrimaryNestedProp(fieldName: string): string | null { return _formatter.getArrayPrimaryNestedProp(fieldName) }

function formatDate(date: string): string { return DatetimeUtil.formatDate(date) }
function isDateField(field: string): boolean { return _inspector.isDateField(field) }

onMounted(() => {
  find()

  const paramId = route.params.id
  const id = props.entityDefinitionId || (Array.isArray(paramId) ? paramId[0] : paramId)

  if (!id) {
    displayAlert("Missing entity ID.")
    return
  }

  entityDefinitionService.findById(id)
    .then((definition: EntityDefinition) => {
      entityDefinition.value = definition
      entityDefinitionProperties.value = definition.schema.properties

      _inspector.setEntityDefinitionProperties(entityDefinitionProperties.value)

      for (const property of entityDefinitionProperties.value) {
        if (property) {
          const fieldName = property.name[0].toUpperCase() + property.name.slice(1)
          let sortable = true
          if (
            ['ref', 'array', 'object'].includes(property.type.type) ||
            (property.type.type === 'string' && EntityDefinitionUtil.hasDecorator('Text', property.decorators))
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
          headers.value.push(headerDef)
          keys.value.push(property.name)
        }
      }

      _widthCalc.setHeaders(headers.value)

      find()
    })
    .catch((error: Error) => {
      debug('Error during entityDefinition retrieval: %O', error)
      displayAlert(error.message)
    })
})

onBeforeUnmount(() => {
  _flushPendingResize(_pendingResizeKind.value)
  document.removeEventListener('mousemove', onColumnResize)
  document.removeEventListener('mouseup', stopColumnResize)
  document.removeEventListener('mousemove', onNestedColumnResize)
  document.removeEventListener('mouseup', stopNestedColumnResize)
  document.removeEventListener('mousemove', onDeepNestedColumnResize)
  document.removeEventListener('mouseup', stopDeepNestedColumnResize)
  document.removeEventListener('mousemove', onVeryDeepNestedColumnResize)
  document.removeEventListener('mouseup', stopVeryDeepNestedColumnResize)
  document.removeEventListener('mousemove', onUltraDeepNestedColumnResize)
  document.removeEventListener('mouseup', stopUltraDeepNestedColumnResize)
})

function onPage(event: any) {
  options.value.rows = event.rows
  options.value.first = event.first
  find()
}

function clearSearch() {
  searchText.value = null
  options.value.first = 0
  find()
}

function search() {
  options.value.first = 0
  find()
}

function displayAlert(text: string) {
  debug('Alert displayed: %s', text)
  window.alert(text)
}

function find() {
  if (loading.value) return

  loading.value = true

  const page = options.value.first / options.value.rows
  const orders: Order[] = []

  if (options.value.sortField) {
    orders.push(new Order(options.value.sortField, options.value.sortOrder === 1 ? Direction.ASC : Direction.DESC))
  }

  const pageable = Pageable.create(page, options.value.rows, { orders })
  const paramId = route.params.id
  const id = props.entityDefinitionId || (Array.isArray(paramId) ? paramId[0] : paramId)

  const queryPromise = (searchText.value?.length)
    ? entitiesService.search(id, searchText.value, pageable)
    : entitiesService.findAll(id, pageable)

  queryPromise
    .then((page: Page<any>) => {
      items.value = page.content ?? []
      totalItems.value = page.totalElements ?? 0
      loading.value = false

      _inspector.setItems(items.value)
      _widthCalc.setItems(items.value)

      if (!finishedInitialLoad.value) {
        setTimeout(() => { finishedInitialLoad.value = true }, 500)
      }
    })
    .catch((error: any) => {
      displayAlert(error.message)
      loading.value = false
      if (!finishedInitialLoad.value) {
        setTimeout(() => { finishedInitialLoad.value = true }, 500)
      }
    })
}

const entityListContext: EntityListContext = {
  get headers() { return headers.value },
  get items() { return items.value },
  rowColors,

  togglePathExpansion,
  isPathExpanded,

  isColumnExpanded,
  isRowCellExpanded,
  isNestedObjectExpanded,
  isDeepNestedExpanded,
  isVeryDeepNestedExpanded,
  isNestedArrayExpanded,

  toggleColumnExpansion,
  toggleRowExpansion,
  toggleNestedObjectExpansion,
  toggleDeepNestedExpansion,
  toggleVeryDeepNestedExpansion,
  toggleNestedArrayExpansion,

  isPrimitiveArrayAtPath,
  getPropertiesAtPath,

  getWidthAtPath,

  isPrimitiveArray,
  isNestedPrimitiveArray,
  isDeepNestedPrimitiveArray,
  getNestedProperties,
  getNestedObjectProperties,
  getDeepNestedProperties,
  getVeryDeepNestedProperties,

  getExpandedColumnWidth,
  getNestedPropWidth,
  getNestedPropRenderWidth,
  getNestedObjectSubColumnWidth,
  getDeepNestedSubColumnWidth,
  getUltraDeepNestedSubColumnWidth,

  startColumnResize,
  startNestedColumnResize,
  startPathResize,

  startDeepNestedColumnResize,
  startVeryDeepNestedColumnResize,
  startUltraDeepNestedColumnResize,

  getCellDisplayValue,
  getCellTitleValue,
  getArrayObjectLabel,
  getArrayPrimaryNestedProp,
  formatDate,
  isDateField
}

provide(ENTITY_LIST_INJECTION_KEY, entityListContext)
</script>

<template>
  <div :class="['entity-list flex h-full w-full flex-col', isDark ? 'entity-list--dark bg-surface-900 text-surface-0' : 'bg-surface-0 text-surface-950']">
    <EntityTableToolbar 
      v-model:searchText="searchText"
      @search="search"
      @clearSearch="clearSearch"
    />

    <div :class="['relative flex-1 overflow-auto', isDark ? 'bg-surface-900' : 'bg-surface-0']" style="min-height: 0;">
      <table class="w-full border-collapse table-fixed" style="box-sizing: border-box;">
        <EntityTableHeaders />
        <EntityTableBody />
      </table>

      <div v-if="items.length === 0 && !loading" :class="['p-8 text-center', isDark ? 'text-surface-200' : 'text-surface-700']">
        <Button label="No Data - Click To Search Again" @click="find" />
      </div>

      <div v-if="loading" :class="['absolute inset-0 flex items-center justify-center', isDark ? 'bg-surface-900/80 text-surface-0' : 'bg-surface-0/75 text-surface-950']">
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

.entity-list--dark :deep(.p-inputtext) {
  border-color: var(--p-surface-600) !important;
  background: var(--p-surface-950) !important;
  color: var(--p-surface-0) !important;
  box-shadow: none !important;
}

.entity-list--dark :deep(.p-inputtext::placeholder) {
  color: var(--p-surface-400) !important;
}

.entity-list--dark :deep(.p-button) {
  box-shadow: none !important;
}

.entity-list--dark :deep(.border-gray-300) {
  border-color: var(--p-surface-700) !important;
}

.entity-list--dark :deep(.border-gray-200) {
  border-color: var(--p-surface-700) !important;
}

.entity-list--dark :deep(.bg-gray-50) {
  background-color: var(--p-surface-800) !important;
}

.entity-list--dark :deep(.hover\:bg-gray-50:hover),
.entity-list--dark :deep(.hover\:bg-gray-100:hover) {
  background-color: var(--p-surface-700) !important;
}

.entity-list--dark :deep(.text-gray-400) {
  color: var(--p-surface-400) !important;
}

.entity-list--dark :deep(.text-gray-500),
.entity-list--dark :deep(.text-gray-600) {
  color: var(--p-surface-400) !important;
}

.entity-list--dark :deep(td),
.entity-list--dark :deep(td > div),
.entity-list--dark :deep(td span),
.entity-list--dark :deep(td div) {
  color: inherit;
}
</style>
