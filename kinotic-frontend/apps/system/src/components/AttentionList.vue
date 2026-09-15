<template>
  <div class="overflow-hidden rounded-lg border border-surface">
    <div class="flex items-center gap-2 border-b border-surface px-4 py-2.5 text-sm font-semibold">
      <i class="pi pi-bell text-muted-color" :style="{ fontSize: '13px' }" />
      Needs attention
      <span v-if="items.length > 0"
            class="rounded-full bg-red-500/10 px-1.5 text-xs font-semibold tabular-nums text-red-600 dark:text-red-400">
        {{ items.length }}
      </span>
    </div>
    <div v-if="items.length === 0" class="flex items-center gap-2 px-4 py-3.5 text-sm text-green-600 dark:text-green-400">
      <i class="pi pi-check-circle" />
      Nothing needs an operator right now
    </div>
    <RouterLink
      v-for="item in items"
      :key="item.to + item.text"
      :to="item.to"
      :class="[
        'flex items-center gap-3 border-b border-l-[3px] border-b-surface px-3.5 py-2.5 text-color no-underline transition-colors last:border-b-0 hover:bg-emphasis',
        item.severity === 'danger' ? 'border-l-red-500' : 'border-l-amber-500'
      ]"
      :title="item.detail"
    >
      <i :class="['pi', item.icon, item.severity === 'danger' ? 'text-red-500' : 'text-amber-500']" />
      <span class="min-w-0 flex-1 truncate text-sm">
        {{ item.text }}
        <span class="text-xs text-muted-color"> · {{ item.detail }}</span>
      </span>
      <i class="pi pi-chevron-right text-muted-color" :style="{ fontSize: '10px' }" />
    </RouterLink>
  </div>
</template>

<script setup lang="ts">
import type { AttentionItem } from '@/util/attention'

/** The list of what an operator has to look at, each row leading to the page where it is handled. */
defineProps<{
  items: AttentionItem[]
}>()
</script>
