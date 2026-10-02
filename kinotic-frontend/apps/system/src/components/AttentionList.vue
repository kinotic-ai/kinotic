<template>
  <DashboardSection :icon="Bell" :tint="items.length > 0 ? TINTS.red : TINTS.green" title="Needs attention"
                    :count="items.length" description="What an operator has to look at, each leading to where it is handled.">
    <div v-if="items.length === 0" class="flex items-center gap-2 px-5 py-4 text-sm text-green-700 dark:text-green-300">
      <CircleCheck :size="16" :stroke-width="1.75" aria-hidden="true" />
      Nothing needs an operator right now
    </div>

    <section v-for="group in groups" :key="group.kind" class="border-b border-surface-200 last:border-b-0 dark:border-surface-700">
      <!-- the group's icon sits in a box as wide as the header's tile, so it lines up under it -->
      <h3>
        <button type="button" :aria-expanded="isOpen(group.kind)" @click="toggle(group.kind)"
                :class="['flex w-full items-center gap-3 py-2.5 pl-5 pr-4 text-left transition-colors', TONES[tone(group.kind)].row]">
          <span class="flex w-9 shrink-0 justify-center">
            <span :class="['flex h-7 w-7 items-center justify-center rounded-lg', TONES[tone(group.kind)].tile]">
              <component :is="GROUPS[group.kind].icon" :size="15" :stroke-width="2" aria-hidden="true" />
            </span>
          </span>
          <span class="text-sm font-semibold text-surface-900 dark:text-surface-50">{{ GROUPS[group.kind].label }}</span>
          <span :class="['min-w-[1.375rem] rounded-full px-1.5 py-px text-center text-xs font-semibold tabular-nums', TONES[tone(group.kind)].count]">
            {{ group.items.length }}
          </span>
          <span class="ml-auto text-xs font-medium text-surface-500 dark:text-surface-400">{{ isOpen(group.kind) ? 'Hide' : 'Review' }}</span>
          <ChevronDown :size="15" :stroke-width="1.75"
                       :class="['shrink-0 text-surface-400 transition-transform', isOpen(group.kind) ? 'rotate-180' : '']" aria-hidden="true" />
        </button>
      </h3>
      <ul v-if="isOpen(group.kind)">
        <li v-for="item in group.items" :key="item.to + item.title" class="border-t border-surface-100 first:border-t-0 dark:border-surface-800">
          <RouterLink :to="item.to"
                      class="group flex items-stretch gap-3 pl-5 pr-4 text-color no-underline transition-colors hover:bg-surface-50 dark:hover:bg-surface-800/60">
            <!-- a timeline down from the group's icon: the rail ties the rows to their group, and a dot
                 in the group's colour marks each row level with its title -->
            <span class="relative flex w-9 shrink-0 justify-center" aria-hidden="true">
              <span class="w-px bg-surface-200 dark:bg-surface-700" />
              <span :class="['absolute top-[17px] h-2.5 w-2.5 rounded-full border-2 bg-surface-0 dark:bg-surface-900', TONES[tone(group.kind)].dot]" />
            </span>
            <div class="min-w-0 flex-1 py-3">
              <div class="flex min-w-0 items-center gap-2">
                <span class="truncate text-sm font-semibold text-surface-950 dark:text-surface-0" v-tooltip.top="item.title">{{ item.title }}</span>
                <span v-if="item.badge"
                      class="shrink-0 rounded-md bg-surface-100 px-1.5 py-px font-mono text-[0.6875rem] text-surface-600 dark:bg-surface-800 dark:text-surface-300">{{ item.badge }}</span>
              </div>
              <p v-if="item.reason" class="mt-1 truncate text-[0.8125rem] text-surface-600 dark:text-surface-300" v-tooltip.top="item.reason">{{ shortIds(item.reason) }}</p>
            </div>
            <div class="flex shrink-0 flex-col items-end justify-center gap-0.5 py-3 text-right">
              <span v-if="item.when" class="whitespace-nowrap text-xs font-medium text-surface-700 dark:text-surface-200">{{ item.when }}</span>
              <span v-if="item.owner" class="max-w-[18rem] truncate font-mono text-[0.6875rem] text-muted-color" v-tooltip.top="item.owner">{{ item.owner }}</span>
            </div>
            <ChevronRight :size="14" :stroke-width="1.75" class="shrink-0 self-center text-surface-300 transition-colors group-hover:text-surface-500" aria-hidden="true" />
          </RouterLink>
        </li>
      </ul>
    </section>
  </DashboardSection>
</template>

<script setup lang="ts">
import { computed, markRaw, ref, type Component } from 'vue'
import { Bell, Box, ChevronDown, ChevronRight, CircleAlert, CircleCheck, RefreshCw, Server, TriangleAlert } from '@lucide/vue'
import { DashboardSection, TINTS } from '@kinotic-ai/frontend-common'
import type { AttentionItem } from '@/util/attention'
import { AttentionKind } from '@/util/AttentionKind'

/**
 * The list of what an operator has to look at, grouped by kind with failures first, each row
 * leading to the page where it is handled.
 */
const props = defineProps<{
  items: AttentionItem[]
}>()

/** Each kind's heading, icon, and whether it is a failure rather than a warning; in display order. */
const GROUPS: Record<AttentionKind, { label: string, icon: Component, critical: boolean }> = {
  [AttentionKind.FAILED_RUN]: { label: 'Failed runs', icon: markRaw(CircleAlert), critical: true },
  [AttentionKind.FAILED_WORKLOAD]: { label: 'Failed workloads', icon: markRaw(Box), critical: true },
  [AttentionKind.WORKLOAD_ON_SILENT_NODE]: { label: 'Workloads on unresponsive nodes', icon: markRaw(TriangleAlert), critical: false },
  [AttentionKind.UNREACHABLE_NODE]: { label: 'Unreachable nodes', icon: markRaw(Server), critical: false },
  [AttentionKind.DRAINING_NODE]: { label: 'Draining nodes', icon: markRaw(Server), critical: false },
  [AttentionKind.VERSION_SKEW]: { label: 'Version mismatch', icon: markRaw(RefreshCw), critical: false }
}

// A full uuid in a reason is noise at a glance; its first block identifies it, and the tooltip keeps it whole
const UUID = /\b([0-9a-f]{8})-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/g

function shortIds(text: string): string {
  return text.replace(UUID, '$1…')
}

type Tone = 'critical' | 'warning'

// A failure reads in red and a warning in amber: a tinted row, a tinted icon tile and a solid count
const TONES: Record<Tone, { row: string, tile: string, count: string, dot: string }> = {
  critical: {
    row: 'bg-red-50/60 hover:bg-red-50 dark:bg-red-500/[0.07] dark:hover:bg-red-500/10',
    tile: TINTS.red,
    count: 'bg-red-500 text-white',
    dot: 'border-red-400 dark:border-red-400'
  },
  warning: {
    row: 'bg-amber-50/60 hover:bg-amber-50 dark:bg-amber-500/[0.06] dark:hover:bg-amber-500/10',
    tile: 'bg-amber-100 text-amber-600 dark:bg-amber-500/15 dark:text-amber-300',
    count: 'bg-amber-500 text-white',
    dot: 'border-amber-400 dark:border-amber-400'
  }
}

function tone(kind: AttentionKind): Tone {
  return GROUPS[kind].critical ? 'critical' : 'warning'
}

const groups = computed(() =>
    (Object.keys(GROUPS) as AttentionKind[])
        .map(kind => ({ kind, items: props.items.filter(item => item.kind === kind) }))
        .filter(group => group.items.length > 0))

// Groups open on demand, so the list starts as one line per kind with its count
const openKinds = ref(new Set<AttentionKind>())

function isOpen(kind: AttentionKind): boolean {
  return openKinds.value.has(kind)
}

function toggle(kind: AttentionKind): void {
  const next = new Set(openKinds.value)
  if (next.has(kind)) {
    next.delete(kind)
  } else {
    next.add(kind)
  }
  openKinds.value = next
}
</script>
