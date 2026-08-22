<template>
  <div class="rounded-lg border border-surface">
    <div v-for="row in rows" :key="row.node.stepPath" class="border-b border-surface last:border-b-0">
      <div class="flex items-center gap-2 py-2 pr-3"
           :style="{ paddingLeft: `${row.depth * 1.25 + 0.75}rem` }">
        <button v-if="row.node.children.length > 0"
                class="w-5 shrink-0 text-muted-color hover:text-color"
                type="button"
                @click="toggle(row.node.stepPath)">
          <i :class="collapsed.has(row.node.stepPath) ? 'pi pi-angle-right' : 'pi pi-angle-down'"
             class="text-xs" />
        </button>
        <span v-else class="w-5 shrink-0" />

        <span class="relative flex h-2.5 w-2.5 shrink-0">
          <span v-if="row.node.status === ExecutionStatus.RUNNING"
                class="absolute inline-flex h-full w-full animate-ping rounded-full bg-sky-400 opacity-60" />
          <span class="relative inline-flex h-2.5 w-2.5 rounded-full" :class="dotClass(row.node)" />
        </span>

        <span class="truncate text-sm"
              :class="row.node.status === ExecutionStatus.PENDING ? 'text-muted-color' : ''"
              :title="row.node.description">{{ row.node.description || `Step ${row.node.sequence}` }}</span>
        <i v-if="row.node.dynamicSteps"
           v-tooltip.top="'This step generated further steps while running'"
           class="pi pi-sitemap shrink-0 text-xs text-muted-color" />

        <span class="ml-auto shrink-0 font-mono text-xs text-muted-color">{{ row.node.stepPath }}</span>
        <span class="w-16 shrink-0 text-right text-xs text-muted-color">
          {{ formatDuration(row.node.started, row.node.finished, now) }}
        </span>
      </div>

      <div v-if="row.node.progress && row.node.status === ExecutionStatus.RUNNING"
           class="pb-2 pr-3"
           :style="{ paddingLeft: `${row.depth * 1.25 + 2.5}rem` }">
        <ProgressBar :value="row.node.progress.percentageComplete" :show-value="false" class="!h-1.5" />
        <div class="mt-1 truncate text-xs text-muted-color">{{ row.node.progress.message }}</div>
      </div>

      <div v-if="row.node.error"
           class="pb-2 pr-3 text-xs text-red-400"
           :style="{ paddingLeft: `${row.depth * 1.25 + 2.5}rem` }">
        {{ row.node.error }}
      </div>
    </div>

    <div v-if="rows.length === 0" class="p-4 text-sm text-muted-color">
      No steps discovered yet
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import ProgressBar from 'primevue/progressbar'
import { ExecutionStatus } from '@kinotic-ai/os-api'
import type { JobStepNode } from './JobStepNode'
import DatetimeUtil from '../../util/DatetimeUtil'

const formatDuration = DatetimeUtil.formatDuration

interface TreeRow {
  node: JobStepNode
  depth: number
}

/**
 * The run's full step ledger as an indented, collapsible tree. Rows appear as steps are
 * discovered, at any depth; now drives the running rows' elapsed time.
 */
const props = defineProps<{
  root: JobStepNode
  now: number
}>()

const collapsed = ref(new Set<string>())

const rows = computed<TreeRow[]>(() => {
  const out: TreeRow[] = []
  const walk = (nodes: JobStepNode[], depth: number) => {
    for (const node of nodes) {
      out.push({ node, depth })
      if (!collapsed.value.has(node.stepPath)) {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(props.root.children, 0)
  return out
})

function toggle(stepPath: string): void {
  const next = new Set(collapsed.value)
  if (next.has(stepPath)) {
    next.delete(stepPath)
  } else {
    next.add(stepPath)
  }
  collapsed.value = next
}

function dotClass(node: JobStepNode): string {
  let ret: string
  if (node.status === ExecutionStatus.COMPLETED) {
    ret = 'bg-emerald-500'
  } else if (node.status === ExecutionStatus.RUNNING) {
    ret = 'bg-sky-400'
  } else if (node.status === ExecutionStatus.FAILED) {
    ret = 'bg-red-500'
  } else if (node.status === ExecutionStatus.CANCELLED) {
    ret = 'bg-amber-500'
  } else {
    ret = 'bg-[var(--p-content-border-color)]'
  }
  return ret
}
</script>
