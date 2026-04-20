<template>
  <thead class="sticky top-0 z-10">
    <tr style="background-color: var(--p-surface-950);">
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
        class="px-0 py-0 text-left text-xs font-medium text-surface-0"
        style="border: 1px solid var(--p-surface-800);"
      >
        <div class="flex items-center gap-1 h-full px-2 min-w-0 overflow-hidden">
          <span 
            v-if="header.isCollapsable && !el.isPrimitiveArray(header.field)" 
            class="flex-shrink-0 cursor-pointer"
            @click.stop="el.toggleColumnExpansion(header.field)"
          >
            <svg v-if="el.isColumnExpanded(header.field)" width="7" height="4" viewBox="0 0 7 4" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M1 0.5L3.5 3L6 0.5" stroke="var(--p-primary-400)" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <svg v-else width="4" height="7" viewBox="0 0 4 7" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M0.5 1L3 3.5L0.5 6" stroke="var(--p-primary-400)" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
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

    <tr 
      v-for="depth in nestedDepthLevels" 
      :key="'depth-' + depth"
      :style="{ backgroundColor: getHeaderColorForDepth(depth) }"
    >
      <th
        v-for="header in el.headers"
        :key="'d' + depth + '-' + header.field"
        :style="{ 
          width: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px',
          maxWidth: (el.isColumnExpanded(header.field) ? el.getExpandedColumnWidth(header.field) : header.width) + 'px',
          boxSizing: 'border-box', flexShrink: '0', height: '29px',
          border: '1px solid var(--p-surface-0)',
          color: getHeaderTextColorForDepth()
        }"
        class="px-0 py-0 text-left text-xs font-medium"
      >
        <div v-if="el.isColumnExpanded(header.field)" class="flex w-full h-full">
          <RecursiveHeaderCell
            :path="[header.field]"
            :targetDepth="depth"
            :currentDepth="1"
          />
        </div>
      </th>
    </tr>
  </thead>
</template>

<script lang="ts">
import { defineComponent, inject, computed, h, type PropType } from 'vue'
import type { EntityListContext } from './types'
import { ENTITY_LIST_INJECTION_KEY } from './types'
import { rowColors } from '@/util/rowColors'

const RecursiveHeaderCell = defineComponent({
  name: 'RecursiveHeaderCell',
  props: {
    path: { type: Array as PropType<string[]>, required: true },
    targetDepth: { type: Number, required: true },
    currentDepth: { type: Number, required: true }
  },
  setup() {
    const el = inject<EntityListContext>(ENTITY_LIST_INJECTION_KEY)!
    return { el }
  },
  render() {
    const { path, targetDepth, currentDepth, el } = this

    const properties = el.getPropertiesAtPath(...path)
    if (!properties || properties.length === 0) return null

    const children: any[] = []

    for (let i = 0; i < properties.length; i++) {
      const prop = properties[i]
      const childPath = [...path, prop.name]
      const isExpandable = (prop.isObject || prop.isArray) && !el.isPrimitiveArrayAtPath(...childPath)
      const isExpanded = isExpandable && el.isPathExpanded(...childPath)

      if (currentDepth === targetDepth) {
        const width = (path.length === 1)
          ? el.getNestedPropRenderWidth(path[0], prop, i) 
          : this.getWidthForProp(path, prop)
        
        const cellChildren: any[] = []

        if (isExpandable) {
          cellChildren.push(
            h('span', {
              class: 'flex-shrink-0 cursor-pointer',
              onClick: (e: MouseEvent) => { e.stopPropagation(); el.togglePathExpansion(...childPath) }
            }, [
              isExpanded
                ? h('svg', { width: '7', height: '4', viewBox: '0 0 7 4', fill: 'none', xmlns: 'http://www.w3.org/2000/svg' }, [
                    h('path', { d: 'M1 0.5L3.5 3L6 0.5', stroke: 'var(--p-surface-700)', 'stroke-width': '1', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' })
                  ])
                : h('svg', { width: '4', height: '7', viewBox: '0 0 4 7', fill: 'none', xmlns: 'http://www.w3.org/2000/svg' }, [
                    h('path', { d: 'M0.5 1L3 3.5L0.5 6', stroke: 'var(--p-surface-700)', 'stroke-width': '1', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' })
                  ])
            ])
          )
        }

        cellChildren.push(
          h('span', {
            class: ['truncate-text text-xs flex-1', { 'cursor-pointer': isExpandable }],
            title: prop.name,
            onClick: (e: MouseEvent) => {
              e.stopPropagation()
              if (isExpandable) el.togglePathExpansion(...childPath)
            }
          }, prop.name)
        )

        cellChildren.push(
          h('div', {
            class: 'resize-handle',
            title: 'Drag to resize',
            onMousedown: (e: MouseEvent) => {
              e.stopPropagation()
              e.preventDefault()
              if (path.length === 1) {
                el.startNestedColumnResize(e, path[0], prop)
              } else {
                el.startPathResize(e, path, prop)
              }
            }
          })
        )

        children.push(
          h('div', {
            key: childPath.join('.'),
            class: 'px-2 py-1 border-r last:border-r-0 relative',
            style: {
              width: width + 'px',
              minWidth: '80px',
              boxSizing: 'border-box',
              flexShrink: '0',
              height: '29px',
              display: 'flex',
              alignItems: 'center',
              borderColor: 'var(--p-surface-0)',
              color: 'var(--p-surface-800)'
            }
          }, [
            h('div', { class: 'flex items-center gap-1 min-w-0 overflow-hidden w-full' }, cellChildren)
          ])
        )
      } else if (currentDepth < targetDepth && isExpanded) {
        const width = (path.length === 1)
          ? el.getNestedPropRenderWidth(path[0], prop, i)
          : this.getWidthForProp(path, prop)

        children.push(
          h('div', {
            key: childPath.join('.'),
            class: 'border-r last:border-r-0',
            style: {
              width: width + 'px',
              minWidth: '80px',
              boxSizing: 'border-box',
              flexShrink: '0',
              borderColor: 'var(--p-surface-0)'
            }
          }, [
            h('div', { class: 'flex w-full h-full' }, [
              h(RecursiveHeaderCell, {
                path: childPath,
                targetDepth: targetDepth,
                currentDepth: currentDepth + 1
              })
            ])
          ])
        )
      } else {
        const width = (path.length === 1)
          ? el.getNestedPropRenderWidth(path[0], prop, i)
          : this.getWidthForProp(path, prop)

        children.push(
          h('div', {
            key: childPath.join('.'),
            class: 'border-r last:border-r-0',
            style: {
              width: width + 'px',
              minWidth: '80px',
              boxSizing: 'border-box',
              flexShrink: '0',
              borderColor: 'var(--p-surface-0)'
            }
          })
        )
      }
    }

    return children
  },
  methods: {
    getWidthForProp(parentPath: string[], prop: any): number {
      const el = this.el
      const childPath = [...parentPath, prop.name]
      const isExpandable = (prop.isObject || prop.isArray) && !el.isPrimitiveArrayAtPath(...childPath)
      const isExpanded = isExpandable && el.isPathExpanded(...childPath)

      if (isExpanded) {
        return el.getWidthAtPath(...childPath)
      }
      return el.getWidthAtPath(...childPath)
    }
  }
})

export default defineComponent({
  name: 'EntityTableHeaders',
  components: { RecursiveHeaderCell },
  setup() {
    const el = inject<EntityListContext>(ENTITY_LIST_INJECTION_KEY)!


    const nestedDepthLevels = computed(() => {
      const maxDepth = getMaxExpandedDepth(el)
      if (maxDepth === 0) return []
      return Array.from({ length: maxDepth }, (_, i) => i + 1)
    })

    function getMaxExpandedDepth(el: EntityListContext): number {
      let max = 0
      for (const header of el.headers) {
        if (!el.isColumnExpanded(header.field) || el.isPrimitiveArray(header.field)) continue
        const depth = getExpandedDepthForPath(el, [header.field])
        if (depth > max) max = depth
      }
      return max
    }

    function getExpandedDepthForPath(el: EntityListContext, path: string[]): number {
      const props = el.getPropertiesAtPath(...path)
      if (!props || props.length === 0) return 1

      let maxChildDepth = 1
      for (const prop of props) {
        const childPath = [...path, prop.name]
        const isExpandable = (prop.isObject || prop.isArray) && !el.isPrimitiveArrayAtPath(...childPath)
        if (isExpandable && el.isPathExpanded(...childPath)) {
          const childDepth = 1 + getExpandedDepthForPath(el, childPath)
          if (childDepth > maxChildDepth) maxChildDepth = childDepth
        }
      }
      return maxChildDepth
    }

    function getHeaderColorForDepth(depth: number): string {
      const baseColors = [
        rowColors[0]?.header || '#ECFCCB',
        rowColors[2]?.header || '#FAE8FF',
        rowColors[3]?.header || '#EDE9FE',
        rowColors[4]?.header || '#7DD3FC'
      ]

      if (depth <= baseColors.length) {
        return baseColors[depth - 1]
      }

      const randomDepth = depth - baseColors.length
      const hue = Math.round((randomDepth * 137.508 + 23) % 360)
      const saturation = 58 + (randomDepth % 4) * 4
      const lightness = 85 + (randomDepth % 3) * 3

      return `hsl(${hue} ${saturation}% ${lightness}%)`
    }

    function getHeaderTextColorForDepth(): string {
      return 'var(--p-surface-800)'
    }

    return { el, nestedDepthLevels, getHeaderColorForDepth, getHeaderTextColorForDepth }
  }
})
</script>
