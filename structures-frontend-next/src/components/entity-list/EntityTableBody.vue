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
          <div class="h-full flex items-stretch">
            <div class="flex-1">
              <template v-if="el.isColumnExpanded(header.field)">
            <template v-if="el.isPrimitiveArray(header.field) && Array.isArray(item[header.field])">
              <template v-if="el.isRowCellExpanded(item.id, header.field)">
                <div>
                  <div class="flex flex-col">
                    <div
                      v-for="(arrItem, arrIdx) in item[header.field]"
                      :key="arrIdx"
                      class="py-1 text-xs cursor-pointer hover:bg-gray-100 border-b last:border-b-0 border-gray-200"
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
                    class="cursor-pointer hover:bg-gray-100 rounded px-1 flex items-center justify-between"
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
              <template v-if="el.isRowCellExpanded(item.id, header.field)">
                <div class="flex w-full">
                  <div
                    v-for="(nestedProp, nestedPropIdx) in el.getNestedProperties(header.field)"
                    :key="`${header.field}.${nestedProp.name}`"
                    class="border-r last:border-r-0 border-gray-300 text-xs"
                    :style="{ 
                      width: el.getNestedPropRenderWidth(header.field, nestedProp, nestedPropIdx) + 'px',
                      minWidth: '80px',
                      boxSizing: 'border-box',
                      flexShrink: '0'
                    }"
                  >
                    <div class="flex flex-col w-full">
                      <div
                        v-for="(arrItem, arrIdx) in item[header.field]"
                        :key="arrIdx"
                        class="px-3 py-2 cursor-pointer hover:bg-gray-100 border-b last:border-b-0 border-gray-200 flex items-center justify-between"
                        :title="String(arrItem[nestedProp.name] || '')"
                        @click="el.toggleRowExpansion(item.id, header.field)"
                      >
                        <span class="truncate">{{ arrItem[nestedProp.name] ?? '-' }}</span>
                        <span v-if="arrIdx === 0 && nestedProp.name === el.getArrayPrimaryNestedProp(header.field)" class="text-gray-500 ml-2 flex-shrink-0">^</span>
                      </div>
                    </div>
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="flex w-full h-[-webkit-fill-available]">
                  <div
                    v-for="(nestedProp, nestedPropIdx) in el.getNestedProperties(header.field)"
                    :key="`${header.field}.${nestedProp.name}`"
                    class="px-3 py-2 border-r last:border-r-0 border-gray-300"
                    :style="{ 
                      width: el.getNestedPropRenderWidth(header.field, nestedProp, nestedPropIdx) + 'px',
                      minWidth: '80px',
                      boxSizing: 'border-box',
                      flexShrink: '0'
                    }"
                  >
                    <div 
                      class="flex items-center justify-between w-full" 
                      :class="{ 'cursor-pointer hover:bg-gray-100 rounded': nestedProp.name === el.getArrayPrimaryNestedProp(header.field) && item[header.field].length > 1 }"
                      :title="String(item[header.field][0]?.[nestedProp.name] || '')"
                      @click="(nestedProp.name === el.getArrayPrimaryNestedProp(header.field) && item[header.field].length > 1) ? el.toggleRowExpansion(item.id, header.field) : null"
                    >
                      <span class="truncate">{{ item[header.field][0]?.[nestedProp.name] ?? '-' }}</span>
                      <span v-if="nestedProp.name === el.getArrayPrimaryNestedProp(header.field) && item[header.field].length > 1" class="text-gray-500 ml-2 flex-shrink-0">
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
                  v-for="nestedProp in el.getNestedProperties(header.field)"
                  :key="`${header.field}.${nestedProp.name}`"
                  class="border-r last:border-r-0 border-gray-300 text-xs"
                  :style="{ 
                    width: el.getNestedPropWidth(header.field, nestedProp) + 'px',
                    minWidth: '80px',
                    flexShrink: '0',
                    boxSizing: 'border-box'
                  }"
                >
                  <template v-if="nestedProp.isArray && el.isNestedObjectExpanded(header.field, nestedProp.name) && Array.isArray(item[header.field]?.[nestedProp.name])">
                    <div class="flex w-full h-full">
                      <div
                        v-for="arrayProp in el.getNestedObjectProperties(header.field, nestedProp.name)"
                        :key="`${header.field}.${nestedProp.name}.${arrayProp.name}`"
                        class="border-r last:border-r-0 border-gray-300"
                        :style="{ 
                          width: (() => {
                            if (arrayProp.type?.type === 'array' && el.isDeepNestedExpanded(header.field, nestedProp.name, arrayProp.name)) {
                              const subProps = el.getDeepNestedProperties(header.field, nestedProp.name, arrayProp.name)
                              return subProps.reduce((total: number, sp: any) => {
                                if (sp.type?.type === 'array' && el.isVeryDeepNestedExpanded(header.field, nestedProp.name, arrayProp.name, sp.name)) {
                                  const veryDeepProps = el.getVeryDeepNestedProperties(header.field, nestedProp.name, arrayProp.name, sp.name)
                                  return total + veryDeepProps.reduce((sum: number, vdp: any) => sum + el.getUltraDeepNestedSubColumnWidth(header.field, nestedProp.name, arrayProp.name, sp.name, vdp.name), 0)
                                }
                                return total + el.getDeepNestedSubColumnWidth(header.field, nestedProp.name, arrayProp.name, sp.name)
                              }, 0)
                            }
                            return el.getNestedObjectSubColumnWidth(header.field, nestedProp.name, arrayProp.name)
                          })() + 'px', 
                          minWidth: '100px',
                          flexShrink: '0'
                        }"
                      >
                        <template v-if="arrayProp.isUnionArray && el.isDeepNestedExpanded(header.field, nestedProp.name, arrayProp.name)">
                          <div class="flex w-full h-full">
                              <template
                              v-for="subProp in el.getDeepNestedProperties(header.field, nestedProp.name, arrayProp.name)"
                              :key="`${header.field}.${nestedProp.name}.${arrayProp.name}.${subProp.name}`"
                            >
                              <template v-if="subProp.type?.type === 'array' && el.isVeryDeepNestedExpanded(header.field, nestedProp.name, arrayProp.name, subProp.name)">
                                <div
                                  v-for="veryDeepProp in el.getVeryDeepNestedProperties(header.field, nestedProp.name, arrayProp.name, subProp.name)"
                                  :key="`${header.field}.${nestedProp.name}.${arrayProp.name}.${subProp.name}.${veryDeepProp.name}`"
                                  class="border-r last:border-r-0 border-gray-300 cursor-pointer hover:bg-sky-50"
                                  :style="{ 
                                    width: el.getUltraDeepNestedSubColumnWidth(header.field, nestedProp.name, arrayProp.name, subProp.name, veryDeepProp.name) + 'px', 
                                    minWidth: '100px',
                                    boxSizing: 'border-box',
                                    flexShrink: '0',
                                    display: 'flex',
                                    alignItems: 'start',
                                  }"
                                  @click.stop="(() => {
                                    const parentArray = item[header.field]?.[nestedProp.name]
                                    if (Array.isArray(parentArray) && parentArray.length > 0) {
                                      const researchArray = parentArray[0]?.[arrayProp.name]
                                      if (Array.isArray(researchArray) && researchArray.length > 0) {
                                        el.toggleNestedArrayExpansion(item.id, `${header.field}.${nestedProp.name}.${arrayProp.name}.0`, subProp.name)
                                      }
                                    }
                                  })()"
                                >
                                  <div class="flex flex-col w-full">
                                    <div
                                      v-for="(arrItem, arrIdx) in (() => {
                                        const parentArray = item[header.field]?.[nestedProp.name]
                                        if (!Array.isArray(parentArray) || parentArray.length === 0) return []
                                        const researchArray = parentArray[0]?.[arrayProp.name]
                                        if (!Array.isArray(researchArray) || researchArray.length === 0) return []
                                        const isRowExpanded = el.isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)
                                        return isRowExpanded ? researchArray : [researchArray[0]]
                                      })()"
                                      :key="arrIdx"
                                      class="border-b last:border-b-0 border-gray-200"
                                    >
                                      <div
                                        v-for="(fundingItem, fundingIdx) in (() => {
                                          const fundingArray = arrItem?.[subProp.name]
                                          if (!Array.isArray(fundingArray) || fundingArray.length === 0) return []
                                          const isFundingExpanded = el.isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}.${arrayProp.name}.${arrIdx}`, subProp.name)
                                          return isFundingExpanded ? fundingArray : [fundingArray[0]]
                                        })()"
                                        :key="fundingIdx"
                                        class="py-2 border-b last:border-b-0 border-gray-300 flex items-center justify-between px-3"
                                        :title="String(fundingItem?.[veryDeepProp.name] ?? '')"
                                      >
                                        <span class="truncate">{{ fundingItem?.[veryDeepProp.name] ?? '-' }}</span>
                                        <span 
                                          v-if="fundingIdx === 0 && veryDeepProp.name === el.getVeryDeepNestedProperties(header.field, nestedProp.name, arrayProp.name, subProp.name)[0].name && el.isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}.${arrayProp.name}.${arrIdx}`, subProp.name)" 
                                          class="text-gray-500 ml-2 flex-shrink-0"
                                        >^</span>
                                        <span 
                                          v-if="fundingIdx === 0 && veryDeepProp.name === el.getVeryDeepNestedProperties(header.field, nestedProp.name, arrayProp.name, subProp.name)[0].name && !el.isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}.${arrayProp.name}.${arrIdx}`, subProp.name) && (arrItem?.[subProp.name]?.length ?? 0) > 1" 
                                          class="text-gray-500 ml-2 flex-shrink-0"
                                        >+{{ (arrItem?.[subProp.name]?.length ?? 1) - 1 }}</span>
                                      </div>
                                    </div>
                                  </div>
                                </div>
                              </template>
                              
                              <div
                                v-if="!(subProp.type?.type === 'array' && el.isVeryDeepNestedExpanded(header.field, nestedProp.name, arrayProp.name, subProp.name))"
                                class="border-r last:border-r-0 border-gray-300"
                                :style="{ 
                                  width: el.getDeepNestedSubColumnWidth(header.field, nestedProp.name, arrayProp.name, subProp.name) + 'px', 
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
                                      const isRowExpanded = el.isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)
                                      return isRowExpanded ? researchArray : [researchArray[0]]
                                    })()"
                                    :key="arrIdx"
                                    class="border-b last:border-b-0 border-gray-200"
                                  >
                                    <template v-if="Array.isArray(arrItem?.[subProp.name])">
                                      <div 
                                        class="cursor-pointer hover:bg-gray-100 rounded"
                                        @click.stop="el.toggleNestedArrayExpansion(item.id, `${header.field}.${nestedProp.name}.${arrayProp.name}.${arrIdx}`, subProp.name)"
                                      >
                                        <div v-if="el.isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}.${arrayProp.name}.${arrIdx}`, subProp.name)" class="flex flex-col">
                                          <div
                                            v-for="(nestedItem, nestedIdx) in arrItem[subProp.name]"
                                            :key="nestedIdx"
                                            class="px-3 py-2 border-b last:border-b-0 border-gray-300 flex items-center justify-between"
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
                                          <span v-if="arrIdx === 0 && subProp.name === el.getDeepNestedProperties(header.field, nestedProp.name, arrayProp.name)[0].name" class="text-gray-500 ml-2 flex-shrink-0">
                                            {{ 
                                              (() => {
                                                const parentArray = item[header.field]?.[nestedProp.name]
                                                if (!Array.isArray(parentArray) || parentArray.length === 0) return ''
                                                const researchArray = parentArray[0]?.[arrayProp.name]
                                                if (!Array.isArray(researchArray) || researchArray.length <= 1) return ''
                                                const isRowExpanded = el.isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)
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
                                        @click.stop="el.toggleNestedArrayExpansion(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)"
                                      >
                                        <span class="truncate">{{ arrItem?.[subProp.name] ?? '-' }}</span>
                                        <span v-if="arrIdx === 0 && subProp.name === el.getDeepNestedProperties(header.field, nestedProp.name, arrayProp.name)[0].name" class="text-gray-500 ml-2 flex-shrink-0">
                                          {{ 
                                            (() => {
                                              const parentArray = item[header.field]?.[nestedProp.name]
                                              if (!Array.isArray(parentArray) || parentArray.length === 0) return ''
                                              const researchArray = parentArray[0]?.[arrayProp.name]
                                              if (!Array.isArray(researchArray) || researchArray.length <= 1) return ''
                                              const isRowExpanded = el.isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)
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
                        <template v-else-if="arrayProp.isUnionArray && el.isNestedArrayExpanded(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)">
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
                              @click="el.toggleNestedArrayExpansion(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name)"
                            >
                              <span class="truncate" :title="String(arrItem?.type || arrItem || '')">{{ arrItem?.type || arrItem || '-' }}</span>
                              <span v-if="arrIdx === 0" class="text-gray-500 ml-2 flex-shrink-0">^</span>
                            </div>
                          </div>
                        </template>
                        <template v-else-if="arrayProp.type?.type === 'array' && !arrayProp.isUnionArray && el.isDeepNestedExpanded(header.field, nestedProp.name, arrayProp.name)">
                          <div class="flex w-full h-full">
                            <div
                              v-for="subProp in el.getDeepNestedProperties(header.field, nestedProp.name, arrayProp.name)"
                              :key="`${header.field}.${nestedProp.name}.${arrayProp.name}.${subProp.name}`"
                              class="px-2 py-2 border-r last:border-r-0 border-gray-300"
                              :style="{ width: el.getDeepNestedSubColumnWidth(header.field, nestedProp.name, arrayProp.name, subProp.name) + 'px', minWidth: '100px' }"
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
                            @click="arrayProp.type?.type === 'array' ? el.toggleNestedArrayExpansion(item.id, `${header.field}.${nestedProp.name}`, arrayProp.name) : null"
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
                  <template v-else-if="nestedProp.isObject && el.isNestedObjectExpanded(header.field, nestedProp.name)">
                    <div class="flex w-full h-full">
                      <div
                        v-for="deepProp in el.getNestedObjectProperties(header.field, nestedProp.name)"
                        :key="`${header.field}.${nestedProp.name}.${deepProp.name}`"
                        class="px-2 py-2 border-r last:border-r-0 border-gray-300"
                        :style="{ width: el.getNestedObjectSubColumnWidth(header.field, nestedProp.name, deepProp.name) + 'px', minWidth: '100px' }"
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
              v-if="el.isRowCellExpanded(item.id, header.field) && header.isCollapsable"
              class="bg-gray-50"
            >
              <div class="flex flex-col">
                <template v-if="Array.isArray(item[header.field])">
                  <div
                    v-for="(arrItem, arrIdx) in item[header.field]"
                    :key="arrIdx"
                    class="p-2! text-xs cursor-pointer hover:bg-gray-100 border-b last:border-b-0 border-gray-200"
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
                :title="el.getCellTitleValue(item, header.field)"
                @click="header.isCollapsable ? el.toggleRowExpansion(item.id, header.field) : null"
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
import { defineComponent, inject } from 'vue'
import type { EntityListContext } from './types'
import { ENTITY_LIST_INJECTION_KEY } from './types'

export default defineComponent({
  name: 'EntityTableBody',
  setup() {
    const el = inject<EntityListContext>(ENTITY_LIST_INJECTION_KEY)!
    return { el }
  }
})
</script>
