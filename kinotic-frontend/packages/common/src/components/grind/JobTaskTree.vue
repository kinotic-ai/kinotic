<template>
  <div class="overflow-hidden rounded-xl border border-surface-200 dark:border-surface-700">
    <div v-for="row in rows" :key="row.node.taskPath"
         class="border-b border-surface-200 last:border-b-0 dark:border-surface-700"
         :class="row.node.status === ExecutionStatus.RUNNING ? 'bg-sky-50 dark:bg-sky-500/10' : ''">
      <div class="flex items-center gap-2.5 py-2.5 pr-3"
           :style="{ paddingLeft: `${row.depth * 1.25 + 0.75}rem` }">
        <button v-if="row.node.children.length > 0"
                class="w-5 shrink-0 text-muted-color hover:text-color"
                type="button"
                @click="toggle(row.node.taskPath)">
          <component :is="collapsed.has(row.node.taskPath) ? ChevronRight : ChevronDown" :size="14" :stroke-width="2" aria-hidden="true" />
        </button>
        <span v-else class="w-5 shrink-0" />

        <span class="flex h-5 w-5 shrink-0 items-center justify-center">
          <component :is="TASK_STATUS_STYLE[row.node.status].rowIcon" :size="18" :stroke-width="2"
                     :class="TASK_STATUS_STYLE[row.node.status].row" aria-hidden="true" />
        </span>

        <span class="truncate text-sm"
              :class="row.node.status === ExecutionStatus.PENDING ? 'text-muted-color' : 'font-medium text-surface-900 dark:text-surface-50'"
              v-tooltip.top="row.node.description">{{ row.node.description || `Task ${row.node.sequence}` }}</span>
        <i v-if="row.node.dynamicTasks"
           v-tooltip.top="'This task generated further tasks while running'"
           class="pi pi-sitemap shrink-0 text-xs text-muted-color" />

        <span class="ml-auto shrink-0 font-mono text-xs text-muted-color">{{ row.node.taskPath }}</span>
        <span class="w-16 shrink-0 text-right font-mono text-xs tabular-nums"
              :class="row.node.status === ExecutionStatus.RUNNING ? 'text-sky-600 dark:text-sky-300' : 'text-muted-color'">
          {{ formatDuration(row.node.started, row.node.finished, now) }}
        </span>
        <button v-if="props.expandable?.(row.node)"
                class="w-5 shrink-0 text-muted-color hover:text-color"
                type="button"
                :aria-expanded="detailOpen(row.node)"
                @click="toggleDetail(row.node.taskPath)">
          <component :is="detailOpen(row.node) ? ChevronUp : ChevronDown" :size="14" :stroke-width="2" aria-hidden="true" />
        </button>
        <span v-else class="w-5 shrink-0" />
      </div>
      <div v-if="props.expandable?.(row.node) && detailOpen(row.node)"
           class="pb-2 pr-3"
           :style="{ paddingLeft: `${row.depth * 1.25 + 2.5}rem` }">
        <slot name="detail" :node="row.node" />
      </div>

      <div v-if="row.node.progress && row.node.status === ExecutionStatus.RUNNING"
           class="pb-2 pr-3"
           :style="{ paddingLeft: `${row.depth * 1.25 + 2.5}rem` }">
        <ProgressBar :value="row.node.progress.percentageComplete" :show-value="false" class="!h-1.5" />
        <div class="mt-1 truncate text-xs text-muted-color">{{ row.node.progress.message }}</div>
      </div>

      <div v-if="row.node.error"
           class="pb-2 pr-3 text-xs text-red-500"
           :style="{ paddingLeft: `${row.depth * 1.25 + 2.5}rem` }">
        {{ row.node.error }}
      </div>
    </div>

    <div v-if="rows.length === 0" class="p-4 text-sm text-muted-color">
      No tasks discovered yet
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import ProgressBar from 'primevue/progressbar'
import { ChevronDown, ChevronRight, ChevronUp } from '@lucide/vue'
import { ExecutionStatus } from '@kinotic-ai/management-api'
import type { JobTaskNode } from './JobTaskNode'
import DatetimeUtil from '../../util/DatetimeUtil'
import { TASK_STATUS_STYLE } from './jobRunDisplay'

const formatDuration = DatetimeUtil.formatDuration

interface TreeRow {
  node: JobTaskNode
  depth: number
}

/**
 * The run's full task ledger as an indented, collapsible tree. Rows appear as tasks are
 * discovered, at any depth; now drives the running rows' elapsed time. A row expandable
 * per the given predicate carries the detail slot beneath it, open while the task runs
 * unless toggled shut, and closed otherwise unless toggled open.
 */
const props = defineProps<{
  root: JobTaskNode
  now: number
  expandable?: (node: JobTaskNode) => boolean
}>()

defineSlots<{
  detail(props: { node: JobTaskNode }): unknown
}>()

const collapsed = ref(new Set<string>())
// Paths whose detail the user flipped away from its default of "open while running"
const detailToggled = ref(new Set<string>())

const rows = computed<TreeRow[]>(() => {
  const out: TreeRow[] = []
  const walk = (nodes: JobTaskNode[], depth: number) => {
    for (const node of nodes) {
      out.push({ node, depth })
      if (!collapsed.value.has(node.taskPath)) {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(props.root.children, 0)
  return out
})

function toggle(taskPath: string): void {
  const next = new Set(collapsed.value)
  if (next.has(taskPath)) {
    next.delete(taskPath)
  } else {
    next.add(taskPath)
  }
  collapsed.value = next
}

function detailOpen(node: JobTaskNode): boolean {
  return (node.status === ExecutionStatus.RUNNING) !== detailToggled.value.has(node.taskPath)
}

function toggleDetail(taskPath: string): void {
  const next = new Set(detailToggled.value)
  if (next.has(taskPath)) {
    next.delete(taskPath)
  } else {
    next.add(taskPath)
  }
  detailToggled.value = next
}
</script>
