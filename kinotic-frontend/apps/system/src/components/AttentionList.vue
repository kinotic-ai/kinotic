<template>
  <DashboardSection :icon="Bell" :tint="items.length > 0 ? TINTS.red : TINTS.green" title="Needs attention"
                    :count="items.length" description="What an operator has to look at, each leading to where it is handled.">
    <div v-if="items.length === 0" class="flex items-center gap-2 px-5 py-4 text-sm text-green-700 dark:text-green-300">
      <CircleCheck :size="16" :stroke-width="1.75" aria-hidden="true" />
      Nothing needs an operator right now
    </div>
    <RouterLink
      v-for="item in items"
      :key="item.to + item.text"
      :to="item.to"
      :class="[
        'flex items-center gap-3 border-b border-l-[3px] border-b-surface-200 px-4 py-2.5 text-color no-underline transition-colors last:border-b-0 hover:bg-surface-100 dark:border-b-surface-700 dark:hover:bg-surface-800',
        item.severity === 'danger' ? 'border-l-red-500' : 'border-l-amber-500'
      ]"
      v-tooltip.top="item.detail"
    >
      <i :class="['pi', item.icon, item.severity === 'danger' ? 'text-red-500' : 'text-amber-500']" />
      <span class="min-w-0 flex-1 truncate text-sm">
        {{ item.text }}
        <span class="text-xs text-muted-color"> · {{ item.detail }}</span>
      </span>
      <ChevronRight :size="14" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
    </RouterLink>
  </DashboardSection>
</template>

<script setup lang="ts">
import { Bell, ChevronRight, CircleCheck } from '@lucide/vue'
import { DashboardSection, TINTS } from '@kinotic-ai/frontend-common'
import type { AttentionItem } from '@/util/attention'

/** The list of what an operator has to look at, each row leading to the page where it is handled. */
defineProps<{
  items: AttentionItem[]
}>()
</script>
