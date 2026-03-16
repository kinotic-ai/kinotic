<template>
  <tbody>
    <template v-for="item in el.items" :key="item.id">
      <tr class="hover:bg-gray-50">
        <td
          v-for="header in el.headers"
          :key="header.field"
          :style="{ 
            width: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px',
            maxWidth: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px',
            boxSizing: 'border-box',
            flexShrink: '0'
          }"
          class="border border-gray-300 px-0 py-0 text-sm"
        >
          <div class="h-full flex items-stretch min-h-[40px]">
            <div class="flex-1 flex flex-col">
              <template v-if="el.isColumnExpanded(header.field)">
                <template v-if="el.isPrimitiveArray(header.field) && Array.isArray(item[header.field])">
                  <template v-if="el.isRowCellExpanded(item.id, header.field)">
                    <div>
                      <div class="flex flex-col">
                        <div
                          v-for="(arrItem, arrIdx) in item[header.field]"
                          :key="arrIdx"
                          class="w-full py-1 text-xs cursor-pointer hover:bg-gray-100 border-b last:border-b-0 border-gray-200"
                          :title="String(arrItem)"
                          @click="el.toggleRowExpansion(item.id, header.field)"
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
                        :class="{ 'cursor-pointer hover:bg-gray-100': item[header.field].length > 1 }"
                        class="rounded px-1 flex items-center justify-between"
                        :title="String(item[header.field][0])"
                        @click="item[header.field].length > 1 ? el.toggleRowExpansion(item.id, header.field) : null"
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
                  <ExpandedArrayGrid
                    :item="item"
                    :arrayField="header.field"
                    :arrayValue="item[header.field]"
                  />
                </template>
                <template v-else-if="typeof item[header.field] === 'object' && item[header.field] !== null">
                  <div class="flex w-full flex-1" style="min-height: 0;">
                    <div
                      v-for="(nestedProp, nestedPropIdx) in el.getNestedProperties(header.field)"
                      :key="`${header.field}.${nestedProp.name}`"
                      class="border-r last:border-r-0 border-gray-300 text-xs self-stretch flex flex-col"
                      :style="{ 
                        width: el.getNestedPropRenderWidth(header.field, nestedProp, nestedPropIdx) + 'px',
                        minWidth: '80px',
                        flexShrink: '0',
                        boxSizing: 'border-box'
                      }"
                    >
                      <RecursiveBodyCell
                        :item="item"
                        :parentValue="item[header.field]"
                        :path="[header.field]"
                        :prop="nestedProp"
                        :instancePathKey="header.field"
                      />
                    </div>
                  </div>
                </template>
              </template>

              <template v-else>
                <div
                  v-if="el.isRowCellExpanded(item.id, header.field) && Array.isArray(item[header.field])"
                  class="bg-gray-50"
                >
                  <div class="flex flex-col">
                    <div
                      v-for="(arrItem, arrIdx) in item[header.field]"
                      :key="arrIdx"
                      class="w-full p-2! text-xs cursor-pointer hover:bg-gray-100 border-b last:border-b-0 border-gray-200"
                      @click="el.toggleRowExpansion(item.id, header.field)"
                    >
                      <div class="px-2 flex items-center justify-between">
                        <template v-if="typeof arrItem !== 'object'">
                          <span class="truncate" :title="String(arrItem)">{{ arrItem }}</span>
                        </template>
                        <template v-else>
                          <span class="truncate" :title="el.getArrayObjectLabel(arrItem, header.field)">{{ el.getArrayObjectLabel(arrItem, header.field) }}</span>
                        </template>
                        <span v-if="arrIdx === 0" class="text-gray-500 ml-2 flex-shrink-0">^</span>
                      </div>
                    </div>
                  </div>
                </div>

                <div
                  v-else
                  class="px-2 py-2 flex-1 flex items-center"
                >
                  <div
                    :class="{ 'cursor-pointer hover:bg-gray-100 rounded px-1': Array.isArray(item[header.field]) && item[header.field].length > 1 }"
                    class="truncate w-full"
                    :title="el.getCellTitleValue(item, header.field)"
                    @click="(Array.isArray(item[header.field]) && item[header.field].length > 1) ? el.toggleRowExpansion(item.id, header.field) : null"
                  >
                    {{
                      el.isDateField(header.field)
                        ? el.formatDate(item[header.field])
                        : el.getCellDisplayValue(item, header.field)
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
</template>

<script lang="ts">
import { defineComponent, inject, h, type PropType } from 'vue'
import type { EntityListContext } from './types'
import { ENTITY_LIST_INJECTION_KEY } from './types'

const RecursiveBodyCell = defineComponent({
  name: 'RecursiveBodyCell',
  props: {
    item: { type: Object, required: true },
    parentValue: { type: null, required: true },
    path: { type: Array as PropType<string[]>, required: true },
    prop: { type: Object, required: true },
    sourceArray: { type: Array as PropType<any[]>, default: null },
    instancePathKey: { type: String, default: '' },
  },
  setup() {
    const el = inject<EntityListContext>(ENTITY_LIST_INJECTION_KEY)!
    return { el }
  },
  render() {
    const { parentValue, path, prop, el } = this
    const childPath = [...path, prop.name]
    const isExpandable = (prop.isObject || prop.isArray) && !el.isPrimitiveArrayAtPath(...childPath)
    const isExpanded = isExpandable && el.isPathExpanded(...childPath)

    if (Array.isArray(parentValue)) {
      if (isExpanded) {
        return this.renderExpandedWithinArray(parentValue, childPath)
      }
      return this.renderLeafWithinArray(parentValue, childPath, prop)
    } else if (parentValue && typeof parentValue === 'object') {
      const value = parentValue[prop.name]
      if (isExpanded) {
        return this.renderExpandedWithinObject(value, childPath)
      }
      return this.renderLeafWithinObject(value, prop)
    }

    return h('div', { class: 'px-2 py-2 truncate text-xs flex items-center flex-1' }, '-')
  },
  methods: {
   
    renderExpandedWithinArray(parentArray: any[], childPath: string[]) {
      const el = this.el
      const prop = this.prop
      const childProps = el.getPropertiesAtPath(...childPath)
      if (!childProps || childProps.length === 0) {
        return this.renderLeafWithinArray(parentArray, childPath, this.prop)
      }

      const mappedInstanceKey = this.instancePathKey ? `${this.instancePathKey}.${prop.name}` : childPath.join('.')
      const mappedArray = parentArray.map((arrItem: any) => arrItem?.[prop.name])

      return h('div', { class: 'flex w-full flex-1', style: { minHeight: '0' } },
        childProps.map((subProp: any) => {
          const subPath = [...childPath, subProp.name]
          const width = el.getWidthAtPath(...subPath)

          return h('div', {
            key: subPath.join('.'),
            class: 'border-r last:border-r-0 border-gray-300 self-stretch flex flex-col',
            style: { width: width + 'px', minWidth: '100px', flexShrink: '0', boxSizing: 'border-box' }
          }, [
            h(RecursiveBodyCell, {
              item: this.item,
              parentValue: mappedArray,
              path: childPath,
              prop: subProp,
              sourceArray: this.sourceArray || parentArray,
              instancePathKey: `${mappedInstanceKey}.${subProp.name}`,
            })
          ])
        })
      )
    },


    renderExpandedWithinObject(value: any, childPath: string[]) {
      const el = this.el
      const childProps = el.getPropertiesAtPath(...childPath)
      if (!childProps || childProps.length === 0) {
        return this.renderLeafWithinObject(value, this.prop)
      }

      const valueInstanceKey = this.instancePathKey ? `${this.instancePathKey}.${this.prop.name}` : childPath.join('.')

      return h('div', { class: 'flex w-full flex-1', style: { minHeight: '0' } },
        childProps.map((subProp: any) => {
          const subPath = [...childPath, subProp.name]
          const width = el.getWidthAtPath(...subPath)

          return h('div', {
            key: subPath.join('.'),
            class: 'border-r last:border-r-0 border-gray-300 self-stretch flex flex-col',
            style: { width: width + 'px', minWidth: '100px', flexShrink: '0', boxSizing: 'border-box' }
          }, [
            h(RecursiveBodyCell, {
              item: this.item,
              parentValue: value,
              path: childPath,
              prop: subProp,
              instancePathKey: valueInstanceKey,
            })
          ])
        })
      )
    },

  
    renderLeafWithinArray(parentArray: any[], childPath: string[], prop: any) {
      const el = this.el
      const item = this.item
      const propName = prop.name
      const parentPath = childPath.slice(0, -1)

      const displayArray = this.sourceArray || parentArray
      const arrayFieldName = parentPath[parentPath.length - 1]

      const arrayInstanceKey = this.instancePathKey || parentPath.join('.')
      const isRowExpanded = el.isNestedArrayExpanded(item.id, arrayInstanceKey, '__row')

      const itemsToShow = isRowExpanded ? parentArray : (parentArray.length > 0 ? [parentArray[0]] : [])
      const displayItemsCount = displayArray.length

      const childProps = el.getPropertiesAtPath(...parentPath)
      const primaryPropName = childProps.length > 0 ? childProps[0].name : null

      const cells = itemsToShow.map((arrItem: any, arrIdx: number) => {
        if (Array.isArray(arrItem)) {
          return this.renderInnerArrayLeaf(arrItem, arrIdx, propName, parentPath)
        }

        const value = arrItem?.[propName]
        const isPrimary = propName === primaryPropName
        const canToggleRow = isRowExpanded || displayItemsCount > 1

        if (value === null || value === undefined) {
          return h('div', {
            key: arrIdx,
            class: `w-full px-3 py-2 border-b last:border-b-0 border-gray-200 text-gray-400 flex items-center justify-between${(isPrimary && canToggleRow) ? ' cursor-pointer hover:bg-gray-100' : ''}`,
            ...((isPrimary && canToggleRow) ? { onClick: () => this.toggleArrayRowExpansion(parentPath, arrayFieldName) } : {})
          }, [
            h('span', {}, '-'),
            isPrimary ? this.renderRowIndicator(arrIdx, isRowExpanded, displayItemsCount) : null
          ])
        }

        if (Array.isArray(value)) {
          return this.renderArrayValueCell(value, arrIdx, propName, parentPath)
        }

        if (typeof value === 'object') {
          const objDisplay = this.getObjectDisplayValue(value)
          return h('div', {
            key: arrIdx,
            class: `w-full px-3 py-2 border-b last:border-b-0 border-gray-200 flex items-center justify-between${(isPrimary && canToggleRow) ? ' cursor-pointer hover:bg-gray-100' : ''}`,
            title: String(objDisplay),
            ...((isPrimary && canToggleRow) ? { onClick: () => this.toggleArrayRowExpansion(parentPath, arrayFieldName) } : {})
          }, [
            h('span', { class: 'truncate' }, String(objDisplay)),
            isPrimary ? this.renderRowIndicator(arrIdx, isRowExpanded, displayItemsCount) : null
          ])
        }

        return h('div', {
          key: arrIdx,
          class: `w-full px-3 py-2 border-b last:border-b-0 border-gray-200 flex items-center justify-between${(isPrimary && canToggleRow) ? ' cursor-pointer hover:bg-gray-100' : ''}`,
          title: String(value),
          ...((isPrimary && canToggleRow) ? { onClick: () => this.toggleArrayRowExpansion(parentPath, arrayFieldName) } : {})
        }, [
          h('span', { class: 'truncate' }, String(value)),
          isPrimary ? this.renderRowIndicator(arrIdx, isRowExpanded, displayItemsCount) : null
        ])
      })

      const isSingle = cells.length <= 1
      return h('div', { class: `flex flex-col h-full w-full flex-1${isSingle ? ' justify-center' : ''}` }, cells)
    },

    renderArrayValueCell(value: any[], arrIdx: number, propName: string, parentPath: string[]) {
      const el = this.el
      const item = this.item

      if (value.length === 0) {
        return h('div', {
          key: arrIdx,
          class: 'w-full px-3 py-2 border-b last:border-b-0 border-gray-200 text-gray-400',
        }, '[]')
      }

      const baseKey = this.instancePathKey || parentPath.join('.')
      const subParentPath = `${baseKey}.__i${arrIdx}`
      const isSubExpanded = el.isNestedArrayExpanded(item.id, subParentPath, propName)

      if (isSubExpanded) {
        return h('div', {
          key: arrIdx,
          class: 'w-full border-b last:border-b-0 border-gray-200',
        }, value.map((v: any, vIdx: number) => {
          const display = (typeof v === 'object' && v !== null)
            ? this.getObjectDisplayValue(v)
            : String(v ?? '-')
          return h('div', {
            key: vIdx,
            class: 'w-full px-3 py-1 border-b last:border-b-0 border-gray-100 cursor-pointer hover:bg-gray-100 flex items-center justify-between',
            title: display,
            onClick: (e: MouseEvent) => {
              e.stopPropagation()
              el.toggleNestedArrayExpansion(item.id, subParentPath, propName)
            }
          }, [
            h('span', { class: 'truncate' }, display),
            vIdx === 0 ? h('span', { class: 'text-gray-500 ml-2 flex-shrink-0' }, '^') : null
          ])
        }))
      }

      const firstItem = value[0]
      const display = (typeof firstItem === 'object' && firstItem !== null)
        ? this.getObjectDisplayValue(firstItem)
        : String(firstItem ?? '-')
      const hasMore = value.length > 1

      return h('div', {
        key: arrIdx,
        class: `w-full px-3 py-2 border-b last:border-b-0 border-gray-200 flex items-center justify-between${hasMore ? ' cursor-pointer hover:bg-gray-100' : ''}`,
        title: display,
        ...(hasMore ? { onClick: (e: MouseEvent) => {
          e.stopPropagation()
          el.toggleNestedArrayExpansion(item.id, subParentPath, propName)
        } } : {})
      }, [
        h('span', { class: 'truncate' }, display),
        hasMore ? h('span', { class: 'text-gray-500 ml-2 flex-shrink-0' }, `+${value.length - 1}`) : null
      ])
    },


    renderInnerArrayLeaf(innerArray: any[], outerIdx: number, propName: string, parentPath: string[]) {
      const el = this.el
      const item = this.item

      if (!innerArray || innerArray.length === 0) {
        return h('div', {
          key: outerIdx,
          class: 'w-full px-3 py-2 border-b last:border-b-0 border-gray-200 text-gray-400',
        }, '-')
      }

      const baseKey = this.instancePathKey || parentPath.join('.')
      const subParentPath = `${baseKey}.__i${outerIdx}`
      const innerArrayField = '__items'
      const isSubExpanded = el.isNestedArrayExpanded(item.id, subParentPath, innerArrayField)

      if (isSubExpanded) {
        return h('div', {
          key: outerIdx,
          class: 'w-full border-b last:border-b-0 border-gray-200',
        }, innerArray.map((innerItem: any, innerIdx: number) => {
          const val = innerItem?.[propName]
          const display = (val !== null && val !== undefined)
            ? (typeof val === 'object' ? this.getObjectDisplayValue(val) : String(val))
            : '-'
          return h('div', {
            key: innerIdx,
            class: 'w-full px-3 py-1 border-b last:border-b-0 border-gray-100 cursor-pointer hover:bg-gray-100 flex items-center justify-between',
            title: display,
            onClick: (e: MouseEvent) => {
              e.stopPropagation()
              el.toggleNestedArrayExpansion(item.id, subParentPath, innerArrayField)
            }
          }, [
            h('span', { class: 'truncate' }, display),
            innerIdx === 0 ? h('span', { class: 'text-gray-500 ml-2 flex-shrink-0' }, '^') : null
          ])
        }))
      }

      const firstItem = innerArray[0]
      const val = firstItem?.[propName]
      const display = (val !== null && val !== undefined)
        ? (typeof val === 'object' ? this.getObjectDisplayValue(val) : String(val))
        : '-'
      const hasMore = innerArray.length > 1
      const isEmpty = val === null || val === undefined

      return h('div', {
        key: outerIdx,
        class: `w-full px-3 py-2 border-b last:border-b-0 border-gray-200 flex items-center justify-between${(hasMore && !isEmpty) ? ' cursor-pointer hover:bg-gray-100' : ''}`,
        title: display,
        ...((hasMore && !isEmpty) ? { onClick: (e: MouseEvent) => {
          e.stopPropagation()
          el.toggleNestedArrayExpansion(item.id, subParentPath, innerArrayField)
        } } : {})
      }, [
        h('span', { class: isEmpty ? 'text-gray-400' : 'truncate' }, display),
        (hasMore && !isEmpty) ? h('span', { class: 'text-gray-500 ml-2 flex-shrink-0' }, `+${innerArray.length - 1}`) : null
      ])
    },

    toggleArrayRowExpansion(parentPath: string[], _arrayFieldName: string) {
      const el = this.el
      const item = this.item
      const arrayInstanceKey = this.instancePathKey || parentPath.join('.')
      el.toggleNestedArrayExpansion(item.id, arrayInstanceKey, '__row')
    },

  
    renderRowIndicator(arrIdx: number, isRowExpanded: boolean, totalCount: number) {
      if (arrIdx !== 0) return null
      if (isRowExpanded) {
        return h('span', { class: 'text-gray-500 ml-2 flex-shrink-0' }, '^')
      }
      if (totalCount > 1) {
        return h('span', { class: 'text-gray-500 ml-2 flex-shrink-0' }, `+${totalCount - 1}`)
      }
      return null
    },


    renderLeafWithinObject(value: any, prop: any) {
      if (value === null || value === undefined) {
        return h('div', { class: 'px-2 py-2 text-gray-400 flex items-center flex-1', title: '' }, '-')
      }

      if (Array.isArray(value)) {
        if (value.length === 0) return h('div', { class: 'px-2 py-2 text-gray-400' }, '[]')

        const parentPathKey = this.instancePathKey || this.path.join('.')
        const arrayField = prop.name
        const isArrayExpanded = this.el.isNestedArrayExpanded(this.item.id, parentPathKey, arrayField)

        if (isArrayExpanded) {
          return h('div', { class: 'flex flex-col w-full' },
            value.map((arrItem: any, arrIdx: number) => {
              const display = (typeof arrItem === 'object' && arrItem !== null)
                ? this.getObjectDisplayValue(arrItem)
                : String(arrItem ?? '-')
              return h('div', {
                key: arrIdx,
                class: 'w-full px-2 py-2 border-b last:border-b-0 border-gray-200 cursor-pointer hover:bg-gray-100 flex items-center justify-between',
                title: display,
                onClick: () => this.el.toggleNestedArrayExpansion(this.item.id, parentPathKey, arrayField)
              }, [
                h('span', { class: 'truncate' }, display),
                arrIdx === 0 ? h('span', { class: 'text-gray-500 ml-2 flex-shrink-0' }, '^') : null
              ])
            })
          )
        }

        const firstItem = value[0]
        const display = (typeof firstItem === 'object' && firstItem !== null)
          ? this.getObjectDisplayValue(firstItem)
          : String(firstItem)
        const hasMore = value.length > 1

        return h('div', {
          class: `w-full px-2 py-2 flex items-center h-full justify-between${hasMore ? ' cursor-pointer hover:bg-gray-100' : ''}`,
          title: display,
          ...(hasMore ? { onClick: () => this.el.toggleNestedArrayExpansion(this.item.id, parentPathKey, arrayField) } : {})
        }, [
          h('span', { class: 'truncate' }, display),
          hasMore ? h('span', { class: 'text-gray-500 ml-2 flex-shrink-0' }, `+${value.length - 1}`) : null
        ])
      }

      if (typeof value === 'object') {
        const display = this.getObjectDisplayValue(value)
        return h('div', { class: 'px-2 py-2 flex items-center flex-1', title: display }, [
          h('span', { class: 'w-full truncate text-center' }, display)
        ])
      }

      return h('div', { class: 'px-2 py-2 flex items-center flex-1', title: String(value) }, [
        h('span', { class: 'w-full truncate text-center' }, String(value))
      ])
    },

    getObjectDisplayValue(obj: any): string {
      if (!obj || typeof obj !== 'object') return String(obj ?? '-')
      if (Array.isArray(obj)) {
        if (obj.length === 0) return '[]'
        const first = obj[0]
        if (typeof first === 'object' && first !== null) {
          const val = first.name || first.title || first.type || first.id || Object.values(first)[0]
          return obj.length > 1 ? `${val} +${obj.length - 1}` : String(val ?? '-')
        }
        return obj.length > 1 ? `${first} +${obj.length - 1}` : String(first)
      }
      const firstValue = obj.name || obj.title || obj.type || obj.id || Object.values(obj)[0]
      if (firstValue && typeof firstValue === 'object') {
        // Nested object — recurse
        return this.getObjectDisplayValue(firstValue)
      }
      return String(firstValue ?? '-')
    }
  }
})

const ExpandedArrayGrid = defineComponent({
  name: 'ExpandedArrayGrid',
  props: {
    item: { type: Object, required: true },
    arrayField: { type: String, required: true },
    arrayValue: { type: Array as PropType<any[]>, required: true },
  },
  setup() {
    const el = inject<EntityListContext>(ENTITY_LIST_INJECTION_KEY)!
    return { el }
  },
  render() {
    const el = this.el
    const field = this.arrayField
    const arr = this.arrayValue

    const nestedProps = el.getNestedProperties(field)
    if (!nestedProps || nestedProps.length === 0) {
      return h('div', { class: 'px-2 py-2 text-gray-400 text-xs' }, '-')
    }

    return h('div', { class: 'flex w-full flex-1', style: { minHeight: '0' } },
      nestedProps.map((nestedProp: any, nestedPropIdx: number) => {
        const width = el.getNestedPropRenderWidth(field, nestedProp, nestedPropIdx)

        return h('div', {
          key: `${field}.${nestedProp.name}`,
          class: 'border-r last:border-r-0 border-gray-300 text-xs self-stretch flex flex-col',
          style: { width: width + 'px', minWidth: '80px', flexShrink: '0', boxSizing: 'border-box' }
        }, [
          h(RecursiveBodyCell, {
            item: this.item,
            parentValue: arr,
            path: [field],
            prop: nestedProp,
            sourceArray: arr,
            instancePathKey: `${field}.${nestedProp.name}`,
          })
        ])
      })
    )
  }
})

export default defineComponent({
  name: 'EntityTableBody',
  components: { RecursiveBodyCell, ExpandedArrayGrid },
  setup() {
    const el = inject<EntityListContext>(ENTITY_LIST_INJECTION_KEY)!
    return { el }
  }
})
</script>
