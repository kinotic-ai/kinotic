<template>
  <thead class="sticky top-0 z-10">
    <tr style="background-color: #101010;">
      <th
        v-for="header in el.headers"
        :key="header.field"
        :style="{ 
          width: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px',
          maxWidth: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px',
          boxSizing: 'border-box',
          position: 'relative',
          height: '38px'
        }"
        class="px-0 py-0 text-left text-xs font-medium text-white"
        style="border: 1px solid #28282B;"
      >
        <div class="flex items-center gap-1 h-full px-2 min-w-0 overflow-hidden">
          <span 
            v-if="header.isCollapsable && !el.isPrimitiveArray(header.field)" 
            class="flex-shrink-0 cursor-pointer"
            @click.stop="el.toggleColumnExpansion(header.field)"
          >
            <svg v-if="el.isColumnExpanded(header.field)" width="7" height="4" viewBox="0 0 7 4" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M1 0.5L3.5 3L6 0.5" stroke="#9B87F5" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <svg v-else width="4" height="7" viewBox="0 0 4 7" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M0.5 1L3 3.5L0.5 6" stroke="#9B87F5" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </span>
          <span 
            class="truncate-text flex-1" 
            :title="header.header"
            :class="{ 'cursor-pointer': header.isCollapsable && !el.isPrimitiveArray(header.field) }"
            @click.stop="(header.isCollapsable && !el.isPrimitiveArray(header.field)) ? el.toggleColumnExpansion(header.field) : null"
          >{{ header.header }}</span>
          <div
            class="resize-handle"
            @mousedown.stop.prevent="el.startColumnResize($event, header)"
            title="Drag to resize"
          ></div>
        </div>
      </th>
    </tr>

    <tr v-if="el.headers.some(h => el.isColumnExpanded(h.field) && !el.isPrimitiveArray(h.field))" :style="{ backgroundColor: el.rowColors[0].header }">
      <th
        v-for="header in el.headers"
        :key="'sub-' + header.field"
        :style="{ 
          width: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px',
          maxWidth: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px'
        , boxSizing: 'border-box', flexShrink: '0', height: '29px' }"
        class="px-0 py-0 text-left text-xs font-medium"
        style="border: 1px solid white;"
      >
        <div v-if="el.isColumnExpanded(header.field)" class="flex w-full h-full">
          <div
            v-for="(nestedProp, nestedPropIdx) in el.getNestedProperties(header.field)"
            :key="`${header.field}.${nestedProp.name}`"
            class="px-2 py-1 border-r last:border-r-0 border-white relative"
            :style="{ 
              width: el.getNestedPropRenderWidth(header.field, nestedProp, nestedPropIdx) + 'px',
              minWidth: '80px',
              boxSizing: 'border-box',
              flexShrink: '0',
              height: '29px',
              display: 'flex',
              alignItems: 'center'
              
            }"
          >
            <div class="flex items-center gap-1 min-w-0 overflow-hidden w-full">
              <span 
                v-if="(nestedProp.isObject || nestedProp.isArray) && !el.isNestedPrimitiveArray(header.field, nestedProp.name)" 
                class="flex-shrink-0 cursor-pointer"
                @click.stop="el.toggleNestedObjectExpansion(header.field, nestedProp.name)"
              >
                <svg v-if="el.isNestedObjectExpanded(header.field, nestedProp.name)" width="7" height="4" viewBox="0 0 7 4" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M1 0.5L3.5 3L6 0.5" stroke="#AFB0B8" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                <svg v-else width="4" height="7" viewBox="0 0 4 7" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M0.5 1L3 3.5L0.5 6" stroke="#AFB0B8" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </span>
              <span 
                class="truncate-text text-xs flex-1"
                :title="nestedProp.name"
                :class="{ 'cursor-pointer': (nestedProp.isObject || nestedProp.isArray) && !el.isNestedPrimitiveArray(header.field, nestedProp.name) }"
                @click.stop="((nestedProp.isObject || nestedProp.isArray) && !el.isNestedPrimitiveArray(header.field, nestedProp.name)) ? el.toggleNestedObjectExpansion(header.field, nestedProp.name) : null"
              >{{ nestedProp.name }}</span>
              <div
                class="resize-handle"
                @mousedown.stop.prevent="el.startNestedColumnResize($event, header.field, nestedProp)"
                title="Drag to resize"
              ></div>
            </div>
          </div>
        </div>
      </th>
    </tr>

    <tr v-if="el.headers.some(h => el.isColumnExpanded(h.field) && !el.isPrimitiveArray(h.field) && el.getNestedProperties(h.field).some(p => el.isNestedObjectExpanded(h.field, p.name)))" style="background-color: #FAE8FF;">
      <th
        v-for="header in el.headers"
        :key="'subsub-' + header.field"
        :style="{ 
          width: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px',
          maxWidth: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px'
        , boxSizing: 'border-box', flexShrink: '0', height: '29px' }"
        class="px-0 py-0 text-left text-xs font-medium"
        style="border: 1px solid white;"
      >
        <div v-if="el.isColumnExpanded(header.field)" class="flex w-full h-full">
          <div
            v-for="(nestedProp, nestedPropIdx) in el.getNestedProperties(header.field)"
            :key="`${header.field}.${nestedProp.name}`"
            class="border-r last:border-r-0 border-white"
            :style="{ 
              width: el.getNestedPropRenderWidth(header.field, nestedProp, nestedPropIdx) + 'px',
              minWidth: '80px',
              boxSizing: 'border-box',
              flexShrink: '0'
            }"
          >
            <div v-if="el.isNestedObjectExpanded(header.field, nestedProp.name)" class="flex w-full h-full">
              <div
                v-for="deepProp in el.getNestedObjectProperties(header.field, nestedProp.name)"
                :key="`${header.field}.${nestedProp.name}.${deepProp.name}`"
                class="px-2 py-1 border-r last:border-r-0 border-white relative"
                :style="{ 
                  width: (() => {
                    if (el.isDeepNestedExpanded(header.field, nestedProp.name, deepProp.name)) {
                      const subProps = el.getDeepNestedProperties(header.field, nestedProp.name, deepProp.name)
                      return subProps.reduce((total: number, sp: any) => {
                        if (sp.type?.type === 'array' && el.isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, sp.name)) {
                          const veryDeepProps = el.getVeryDeepNestedProperties(header.field, nestedProp.name, deepProp.name, sp.name)
                          return total + veryDeepProps.reduce((sum: number, vdp: any) => sum + el.getUltraDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, sp.name, vdp.name), 0)
                        }
                        return total + el.getDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, sp.name)
                      }, 0)
                    }
                    return el.getNestedObjectSubColumnWidth(header.field, nestedProp.name, deepProp.name)
                  })() + 'px', 
                  minWidth: '100px',
                  height: '29px',
                  flexShrink: '0'
                }"
              >
                <div class="flex items-center gap-1 h-full min-w-0 overflow-hidden w-full">
                  <span 
                    v-if="(deepProp.type?.type === 'array' || deepProp.type?.type === 'object') && !el.isDeepNestedPrimitiveArray(header.field, nestedProp.name, deepProp.name)" 
                    class="flex-shrink-0 cursor-pointer"
                    @click.stop="el.toggleDeepNestedExpansion(header.field, nestedProp.name, deepProp.name)"
                  >
                    <svg v-if="el.isDeepNestedExpanded(header.field, nestedProp.name, deepProp.name)" width="7" height="4" viewBox="0 0 7 4" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <path d="M1 0.5L3.5 3L6 0.5" stroke="#AFB0B8" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    <svg v-else width="4" height="7" viewBox="0 0 4 7" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <path d="M0.5 1L3 3.5L0.5 6" stroke="#AFB0B8" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                  </span>
                  <span 
                    class="truncate text-xs text-surface-900 flex-1"
                    :title="deepProp.name"
                    :class="{ 'cursor-pointer': (deepProp.type?.type === 'array' || deepProp.type?.type === 'object') && !el.isDeepNestedPrimitiveArray(header.field, nestedProp.name, deepProp.name) }"
                    @click.stop="((deepProp.type?.type === 'array' || deepProp.type?.type === 'object') && !el.isDeepNestedPrimitiveArray(header.field, nestedProp.name, deepProp.name)) ? el.toggleDeepNestedExpansion(header.field, nestedProp.name, deepProp.name) : null"
                  >{{ deepProp.name }}</span>
                  <div
                    class="resize-handle"
                    @mousedown.stop.prevent="el.startDeepNestedColumnResize($event, header.field, nestedProp.name, deepProp)"
                    title="Drag to resize"
                  ></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </th>
    </tr>

    <tr v-if="el.headers.some(h => el.isColumnExpanded(h.field) && el.getNestedProperties(h.field).some(np => el.isNestedObjectExpanded(h.field, np.name) && el.getNestedObjectProperties(h.field, np.name).some(dp => el.isDeepNestedExpanded(h.field, np.name, dp.name))))" style="background-color: #EDE9FE;">
      <th
        v-for="header in el.headers"
        :key="'deep-' + header.field"
        :style="{ 
          width: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px',
          maxWidth: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px'
        , boxSizing: 'border-box', flexShrink: '0', height: '29px' }"
        class="px-0 py-0 text-left text-xs font-medium"
        style="border: 1px solid white;"
      >
        <div v-if="el.isColumnExpanded(header.field)" class="flex w-full h-full">
          <div
            v-for="(nestedProp, nestedPropIdx) in el.getNestedProperties(header.field)"
            :key="`${header.field}.${nestedProp.name}`"
            class="border-r last:border-r-0 border-white"
            :style="{ 
              width: el.getNestedPropRenderWidth(header.field, nestedProp, nestedPropIdx) + 'px',
              minWidth: '80px',
              boxSizing: 'border-box',
              flexShrink: '0'
            }"
          >
            <div v-if="el.isNestedObjectExpanded(header.field, nestedProp.name)" class="flex w-full h-full">
              <div
                v-for="deepProp in el.getNestedObjectProperties(header.field, nestedProp.name)"
                :key="`${header.field}.${nestedProp.name}.${deepProp.name}`"
                class="border-r last:border-r-0 border-white"
                :style="{ 
                  width: (() => {
                    if (el.isDeepNestedExpanded(header.field, nestedProp.name, deepProp.name)) {
                      const subProps = el.getDeepNestedProperties(header.field, nestedProp.name, deepProp.name)
                      return subProps.reduce((total: number, sp: any) => {
                        if (sp.type?.type === 'array' && el.isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, sp.name)) {
                          const veryDeepProps = el.getVeryDeepNestedProperties(header.field, nestedProp.name, deepProp.name, sp.name)
                          return total + veryDeepProps.reduce((sum: number, vdp: any) => sum + el.getUltraDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, sp.name, vdp.name), 0)
                        }
                        return total + el.getDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, sp.name)
                      }, 0)
                    }
                    return el.getNestedObjectSubColumnWidth(header.field, nestedProp.name, deepProp.name)
                  })() + 'px', 
                  minWidth: '80px',
                  boxSizing: 'border-box',
                  flexShrink: '0'
                }"
              >
                <div v-if="el.isDeepNestedExpanded(header.field, nestedProp.name, deepProp.name)" class="flex w-full h-full">
                  <div
                    v-for="subProp in el.getDeepNestedProperties(header.field, nestedProp.name, deepProp.name)"
                    :key="`${header.field}.${nestedProp.name}.${deepProp.name}.${subProp.name}`"
                    class="px-2 py-1 border-r last:border-r-0 border-white relative"
                    :style="{ 
                      width: (() => {
                        if (subProp.type?.type === 'array' && el.isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, subProp.name)) {
                          const veryDeepProps = el.getVeryDeepNestedProperties(header.field, nestedProp.name, deepProp.name, subProp.name)
                          return veryDeepProps.reduce((sum: number, vdp: any) => sum + el.getUltraDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, subProp.name, vdp.name), 0)
                        }
                        return el.getDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, subProp.name)
                      })() + 'px', 
                      minWidth: '100px',
                      flexShrink: '0',
                      boxSizing: 'border-box'
                    }"
                  >
                    <div class="flex items-center gap-1 h-full min-w-0 overflow-hidden w-full">
                      <span 
                        v-if="subProp.type?.type === 'array' && subProp.type?.contains?.type !== 'string' && subProp.type?.contains?.type !== 'number' && subProp.type?.contains?.type !== 'boolean' && subProp.type?.contains?.type !== 'date'" 
                        class="flex-shrink-0 cursor-pointer"
                        @click.stop="el.toggleVeryDeepNestedExpansion(header.field, nestedProp.name, deepProp.name, subProp.name)"
                      >
                        <svg v-if="el.isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, subProp.name)" width="7" height="4" viewBox="0 0 7 4" fill="none" xmlns="http://www.w3.org/2000/svg">
                          <path d="M1 0.5L3.5 3L6 0.5" stroke="#AFB0B8" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                        </svg>
                        <svg v-else width="4" height="7" viewBox="0 0 4 7" fill="none" xmlns="http://www.w3.org/2000/svg">
                          <path d="M0.5 1L3 3.5L0.5 6" stroke="#AFB0B8" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                        </svg>
                      </span>
                      <span 
                        class="truncate text-xs text-surface-900 flex-1"
                        :title="subProp.name"
                        :class="{ 'cursor-pointer': subProp.type?.type === 'array' && subProp.type?.contains?.type !== 'string' && subProp.type?.contains?.type !== 'number' && subProp.type?.contains?.type !== 'boolean' && subProp.type?.contains?.type !== 'date' }"
                        @click.stop="(subProp.type?.type === 'array' && subProp.type?.contains?.type !== 'string' && subProp.type?.contains?.type !== 'number' && subProp.type?.contains?.type !== 'boolean' && subProp.type?.contains?.type !== 'date') ? el.toggleVeryDeepNestedExpansion(header.field, nestedProp.name, deepProp.name, subProp.name) : null"
                      >{{ subProp.name }}</span>
                      <div
                        class="resize-handle"
                        @mousedown.stop.prevent="el.startVeryDeepNestedColumnResize($event, header.field, nestedProp.name, deepProp.name, subProp)"
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

    <tr v-if="el.headers.some(h => el.isColumnExpanded(h.field) && el.getNestedProperties(h.field).some(np => el.isNestedObjectExpanded(h.field, np.name) && el.getNestedObjectProperties(h.field, np.name).some(dp => el.getDeepNestedProperties(h.field, np.name, dp.name).some(vdp => el.isVeryDeepNestedExpanded(h.field, np.name, dp.name, vdp.name)))))" style="background-color: #7dd3fc;">
      <th
        v-for="header in el.headers"
        :key="'very-deep-' + header.field"
        :style="{ 
          width: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px',
          maxWidth: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px'
        , boxSizing: 'border-box', flexShrink: '0' }"
        class="px-0 py-0 text-left text-xs font-medium"
        style="border: 1px solid white;"
      >
        <div v-if="el.isColumnExpanded(header.field)" class="flex w-full">
          <div
            v-for="(nestedProp, nestedPropIdx) in el.getNestedProperties(header.field)"
            :key="`${header.field}.${nestedProp.name}`"
            class="border-r last:border-r-0 border-white"
            :style="{ 
              width: el.getNestedPropRenderWidth(header.field, nestedProp, nestedPropIdx) + 'px',
              minWidth: '80px',
              boxSizing: 'border-box',
              flexShrink: '0'
            }"
          >
            <div v-if="el.isNestedObjectExpanded(header.field, nestedProp.name)" class="flex w-full h-full">
              <div
                v-for="deepProp in el.getNestedObjectProperties(header.field, nestedProp.name)"
                :key="`${header.field}.${nestedProp.name}.${deepProp.name}`"
                class="border-r last:border-r-0 border-white"
                :style="{ 
                  width: (() => {
                    if (el.isDeepNestedExpanded(header.field, nestedProp.name, deepProp.name)) {
                      const subProps = el.getDeepNestedProperties(header.field, nestedProp.name, deepProp.name)
                      return subProps.reduce((total: number, sp: any) => {
                        if (sp.type?.type === 'array' && el.isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, sp.name)) {
                          const veryDeepProps = el.getVeryDeepNestedProperties(header.field, nestedProp.name, deepProp.name, sp.name)
                          return total + veryDeepProps.reduce((sum: number, vdp: any) => sum + el.getUltraDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, sp.name, vdp.name), 0)
                        }
                        return total + el.getDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, sp.name)
                      }, 0)
                    }
                    return el.getNestedObjectSubColumnWidth(header.field, nestedProp.name, deepProp.name)
                  })() + 'px', 
                  minWidth: '100px',
                  flexShrink: '0'
                }"
              >
                <div v-if="el.isDeepNestedExpanded(header.field, nestedProp.name, deepProp.name)" class="flex w-full h-full">
                  <template
                    v-for="subProp in el.getDeepNestedProperties(header.field, nestedProp.name, deepProp.name)"
                    :key="`${header.field}.${nestedProp.name}.${deepProp.name}.${subProp.name}`"
                  >
                    <template v-if="subProp.type?.type === 'array' && el.isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, subProp.name)">
                      <div
                        v-for="veryDeepProp in el.getVeryDeepNestedProperties(header.field, nestedProp.name, deepProp.name, subProp.name)"
                        :key="`${header.field}.${nestedProp.name}.${deepProp.name}.${subProp.name}.${veryDeepProp.name}`"
                        class="border-r last:border-r-0 border-white flex items-center px-2 py-1 relative overflow-hidden min-w-0"
                        :style="{ 
                          width: el.getUltraDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, subProp.name, veryDeepProp.name) + 'px',
                          minWidth: '100px',
                          boxSizing: 'border-box',
                          flexShrink: '0',
                          height: '29px'
                        }"
                      >
                        <span class="truncate text-xs text-gray-800 flex-1" :title="veryDeepProp.name">{{ veryDeepProp.name }}</span>
                        <div
                          class="resize-handle"
                          @mousedown.stop.prevent="el.startUltraDeepNestedColumnResize($event, header.field, nestedProp.name, deepProp.name, subProp.name, veryDeepProp)"
                          title="Drag to resize"
                        ></div>
                      </div>
                    </template>
                    <div
                      v-if="!(subProp.type?.type === 'array' && el.isVeryDeepNestedExpanded(header.field, nestedProp.name, deepProp.name, subProp.name))"
                      class="border-r last:border-r-0 border-gray-300 px-2 py-1"
                      :style="{ 
                        width: el.getDeepNestedSubColumnWidth(header.field, nestedProp.name, deepProp.name, subProp.name) + 'px', 
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
</template>

<script lang="ts">
import { defineComponent, inject } from 'vue'
import type { EntityListContext } from './types'
import { ENTITY_LIST_INJECTION_KEY } from './types'

export default defineComponent({
  name: 'EntityTableHeaders',
  setup() {
    const el = inject<EntityListContext>(ENTITY_LIST_INJECTION_KEY)!
    return { el }
  }
})
</script>
